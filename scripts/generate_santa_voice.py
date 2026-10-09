#!/usr/bin/env python3
"""Génère les répliques audio du Père Noël depuis le script JSON.

L'audio est pré-généré et versionné dans le dépôt, jamais synthétisé à
l'exécution : coût unique, aucune latence pour l'enfant, et surtout le
contenu est auditable — on sait exactement ce qui sera prononcé. La clé
ElevenLabs ne part donc jamais dans l'app iOS.

La clé est lue à la volée depuis 1Password et n'est ni affichée ni écrite.

    ./scripts/generate_santa_voice.py            # ne régénère que le manquant
    ./scripts/generate_santa_voice.py --force    # tout régénérer
    ./scripts/generate_santa_voice.py --dry-run  # lister sans appeler l'API

Piège vérifié : le texte DOIT porter ses accents. Sans eux, le modèle
multilingue bascule sur une prononciation anglaise et lit « Père Noël »
comme « Pernoel ».
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SCRIPT_JSON = RACINE / "ios/GiftManager/Resources/SantaScript/santa_script_fr.json"
DOSSIER_AUDIO = RACINE / "ios/GiftManager/Resources/SantaScript/audio"
EMPREINTES = DOSSIER_AUDIO / "fingerprints.json"

OP_ITEM = "op://giftmanager/dekf7ehq7eophhcnaapq3ul2s4/credential"
API = "https://api.elevenlabs.io/v1/text-to-speech"


def lire_cle() -> str:
    """Lit la clé ElevenLabs depuis 1Password sans jamais l'exposer."""
    env = dict(os.environ)
    jeton = Path("~/.config/op/orca-sa-token").expanduser()
    if jeton.exists():
        env["OP_SERVICE_ACCOUNT_TOKEN"] = jeton.read_text().strip()
    res = subprocess.run(["op", "read", OP_ITEM],
                         capture_output=True, text=True, env=env, timeout=60)
    if res.returncode != 0:
        sys.exit(f"clé ElevenLabs illisible : {res.stderr.strip()[:200]}")
    return res.stdout.strip()


def empreinte(texte: str, reglages: dict) -> str:
    """Identifie texte + réglages, pour ne régénérer que ce qui a changé."""
    donnees = json.dumps({"t": texte, "r": reglages}, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(donnees.encode("utf-8")).hexdigest()[:16]


def verifier_accents(repliques: list[dict]) -> None:
    """Un texte français sans aucun accent est presque sûrement une erreur."""
    suspects = [
        r["id"] for r in repliques
        if len(r["texte"]) > 40 and not any(c in r["texte"] for c in "éèêëàâäîïôöùûüç")
    ]
    if suspects:
        print("  ATTENTION : répliques sans aucun accent, prononciation anglaise probable :")
        for s in suspects:
            print(f"    - {s}")


def synthetiser(cle: str, texte: str, voix: dict) -> bytes:
    corps = json.dumps({
        "text": texte,
        "model_id": voix["model_id"],
        "voice_settings": voix["reglages"],
    }, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(
        f"{API}/{voix['voice_id']}",
        data=corps,
        headers={"xi-api-key": cle, "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=180) as rep:
        return rep.read()


def main() -> int:
    ap = argparse.ArgumentParser(description="Génère les répliques du Père Noël")
    ap.add_argument("--force", action="store_true", help="régénérer même l'inchangé")
    ap.add_argument("--dry-run", action="store_true", help="lister sans appeler l'API")
    args = ap.parse_args()

    script = json.loads(SCRIPT_JSON.read_text(encoding="utf-8"))
    voix = script["voix"]
    repliques = script["repliques"]

    print(f"voix : {voix['nom']} ({voix['model_id']})")
    print(f"{len(repliques)} répliques dans {SCRIPT_JSON.name}")
    verifier_accents(repliques)

    DOSSIER_AUDIO.mkdir(parents=True, exist_ok=True)
    connues = {}
    if EMPREINTES.exists():
        connues = json.loads(EMPREINTES.read_text())

    a_faire = []
    for r in repliques:
        emp = empreinte(r["texte"], voix["reglages"])
        fichier = DOSSIER_AUDIO / f"{r['id']}.mp3"
        if args.force or connues.get(r["id"]) != emp or not fichier.exists():
            a_faire.append((r, emp, fichier))

    if not a_faire:
        print("tout est à jour, rien à générer")
        return 0

    total = sum(len(r["texte"]) for r, _, _ in a_faire)
    print(f"\n{len(a_faire)} réplique(s) à générer, {total} caractères")

    if args.dry_run:
        for r, _, f in a_faire:
            print(f"  [essai] {r['id']:24s} {len(r['texte']):>4} car. -> {f.name}")
        return 0

    cle = lire_cle()
    for r, emp, fichier in a_faire:
        try:
            audio = synthetiser(cle, r["texte"], voix)
        except urllib.error.HTTPError as err:
            detail = err.read().decode()[:200]
            print(f"  {r['id']:24s} ÉCHEC HTTP {err.code} — {detail}")
            return 1
        fichier.write_bytes(audio)
        connues[r["id"]] = emp
        print(f"  {r['id']:24s} OK {len(audio):>7} octets")

    EMPREINTES.write_text(json.dumps(connues, indent=2, sort_keys=True) + "\n")
    print(f"\nempreintes écrites dans {EMPREINTES.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

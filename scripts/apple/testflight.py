# /// script
# requires-python = ">=3.10"
# dependencies = ["pyjwt[crypto]>=2.8", "requests>=2.31"]
# ///
"""Distribution TestFlight d'un build envoyé (#14).

1. attend la fin du traitement Apple du build ;
2. groupe interne « Équipe » (accès à tous les builds) avec les utilisateurs App Store Connect ;
3. groupe externe « Famille » avec lien public ;
4. textes « À tester », infos de test, puis soumission du build à la vérification bêta (requise pour l'externe).

Usage (variables ASC_* et APP_BUNDLE_ID dans l'environnement) :
  uv run scripts/apple/testflight.py <build_number>
"""
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
from asc import call  # noqa: E402

WHATS_NEW = (
    "Première version de Famille Cadeaux\n"
    "• Crée ta famille et invite les autres foyers avec le code (Famille › Paramètres).\n"
    "• Ajoute tes enfants et leurs envies en collant des liens Amazon, Galaxus, Fnac…\n"
    "• Réserve un cadeau pour un autre enfant : personne ne sait que c'est toi.\n"
    "Merci de signaler tout souci avec une capture d'écran depuis TestFlight."
)
DESCRIPTION = (
    "Famille Cadeaux coordonne les cadeaux des enfants entre plusieurs foyers et plusieurs pays : "
    "listes par enfant et par événement (Noël, anniversaires), liens de n'importe quelle boutique, "
    "réservation anonyme pour éviter les doublons."
)
REVIEW_NOTES = (
    "Connexion uniquement avec Sign in with Apple (aucun compte de démo nécessaire). "
    "Après connexion : créer une famille, ajouter un enfant (Famille › Membres), puis ajouter un cadeau "
    "en collant un lien de boutique. Les réservations sont anonymes et invisibles pour les parents de l'enfant."
)


def find_app(bundle_id: str) -> str:
    apps = call("GET", f"/v1/apps?filter[bundleId]={bundle_id}")["data"]
    if not apps:
        sys.exit(f"App {bundle_id} introuvable")
    return apps[0]["id"]


def wait_build(app_id: str, version: str, timeout: int = 2400) -> dict:
    deadline = time.time() + timeout
    while time.time() < deadline:
        builds = call("GET", f"/v1/builds?filter[app]={app_id}&filter[version]={version}&limit=1")["data"]
        if builds:
            state = builds[0]["attributes"]["processingState"]
            print(f"  build {version} : {state}")
            if state == "VALID":
                return builds[0]
            if state in ("FAILED", "INVALID"):
                sys.exit(f"Traitement du build en échec : {state}")
        else:
            print(f"  build {version} pas encore visible…")
        time.sleep(30)
    sys.exit("Délai dépassé pour le traitement du build")


def ensure_group(app_id: str, name: str, internal: bool) -> dict:
    groups = call("GET", f"/v1/apps/{app_id}/betaGroups?limit=50")["data"]
    for group in groups:
        if group["attributes"]["name"] == name:
            return group
    attributes = {"name": name, "isInternalGroup": internal}
    if internal:
        attributes["hasAccessToAllBuilds"] = True
    else:
        attributes.update({"publicLinkEnabled": True, "publicLinkLimitEnabled": True, "publicLinkLimit": 100,
                           "feedbackEnabled": True})
    return call("POST", "/v1/betaGroups", {"data": {"type": "betaGroups", "attributes": attributes,
                                                     "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})["data"]


def add_internal_testers(group_id: str) -> None:
    """Ajoute au groupe interne le seul titulaire du compte : le compte App Store Connect est partagé
    avec d'autres projets, on n'invite donc jamais les autres utilisateurs. Pas d'emoji : Apple les refuse."""
    try:
        users = call("GET", "/v1/users?limit=50")["data"]
    except SystemExit:
        print("  (lecture des utilisateurs impossible avec cette clé : ajout manuel dans TestFlight)")
        return
    for user in users:
        attrs = user["attributes"]
        if "ACCOUNT_HOLDER" not in (attrs.get("roles") or []):
            continue
        email = attrs.get("username")
        if not email:
            continue
        try:
            call("POST", "/v1/betaTesters", {"data": {
                "type": "betaTesters",
                "attributes": {"email": email, "firstName": attrs.get("firstName") or "", "lastName": attrs.get("lastName") or ""},
                "relationships": {"betaGroups": {"data": [{"type": "betaGroups", "id": group_id}]}},
            }})
            print(f"  testeur interne ajouté : {email}")
        except SystemExit:
            print(f"  testeur interne déjà présent ou refusé : {email}")


def ensure_beta_texts(app_id: str, build_id: str) -> None:
    locs = call("GET", f"/v1/apps/{app_id}/betaAppLocalizations")["data"]
    fr = next((l for l in locs if l["attributes"]["locale"] == "fr-FR"), None)
    body = {"description": DESCRIPTION, "feedbackEmail": os.environ.get("FEEDBACK_EMAIL") or None}
    body = {k: v for k, v in body.items() if v}
    if fr:
        call("PATCH", f"/v1/betaAppLocalizations/{fr['id']}", {"data": {"type": "betaAppLocalizations", "id": fr["id"], "attributes": body}})
    else:
        call("POST", "/v1/betaAppLocalizations", {"data": {"type": "betaAppLocalizations",
                                                          "attributes": {"locale": "fr-FR", **body},
                                                          "relationships": {"app": {"data": {"type": "apps", "id": app_id}}}}})

    blocs = call("GET", f"/v1/builds/{build_id}/betaBuildLocalizations")["data"]
    bfr = next((l for l in blocs if l["attributes"]["locale"] == "fr-FR"), None)
    if bfr:
        call("PATCH", f"/v1/betaBuildLocalizations/{bfr['id']}",
             {"data": {"type": "betaBuildLocalizations", "id": bfr["id"], "attributes": {"whatsNew": WHATS_NEW}}})
    else:
        call("POST", "/v1/betaBuildLocalizations", {"data": {"type": "betaBuildLocalizations",
                                                            "attributes": {"locale": "fr-FR", "whatsNew": WHATS_NEW},
                                                            "relationships": {"build": {"data": {"type": "builds", "id": build_id}}}}})


def ensure_review_details(app_id: str) -> bool:
    """Coordonnées de contact pour la vérification bêta. Reprises d'une autre app du compte si absentes."""
    detail = call("GET", f"/v1/apps/{app_id}/betaAppReviewDetail")["data"]
    attrs = detail["attributes"]
    patch = {"demoAccountRequired": False, "notes": REVIEW_NOTES}
    if not attrs.get("contactEmail") or not attrs.get("contactPhone"):
        for other in call("GET", "/v1/apps?limit=50")["data"]:
            if other["id"] == app_id:
                continue
            o = call("GET", f"/v1/apps/{other['id']}/betaAppReviewDetail")["data"]["attributes"]
            if o.get("contactEmail") and o.get("contactPhone"):
                patch.update({k: o[k] for k in ("contactFirstName", "contactLastName", "contactPhone", "contactEmail")})
                print(f"  coordonnées de contact reprises de « {other['attributes']['name']} »")
                break
        else:
            print("  ⚠️  coordonnées de contact manquantes : à renseigner dans TestFlight › Informations de test")
            call("PATCH", f"/v1/betaAppReviewDetails/{detail['id']}", {"data": {"type": "betaAppReviewDetails", "id": detail["id"], "attributes": patch}})
            return False
    call("PATCH", f"/v1/betaAppReviewDetails/{detail['id']}", {"data": {"type": "betaAppReviewDetails", "id": detail["id"], "attributes": patch}})
    return True


def main() -> None:
    version = sys.argv[1]
    app_id = find_app(os.environ["APP_BUNDLE_ID"])
    print("▶ Attente du traitement Apple")
    build = wait_build(app_id, version)

    print("▶ Groupe interne")
    internal = ensure_group(app_id, "Équipe", internal=True)
    add_internal_testers(internal["id"])

    print("▶ Groupe externe « Famille »")
    family = ensure_group(app_id, "Famille", internal=False)
    ensure_beta_texts(app_id, build["id"])
    try:
        call("POST", f"/v1/betaGroups/{family['id']}/relationships/builds", {"data": [{"type": "builds", "id": build["id"]}]})
    except SystemExit:
        print("  build déjà dans le groupe")

    if ensure_review_details(app_id):
        try:
            call("POST", "/v1/betaAppReviewSubmissions",
                 {"data": {"type": "betaAppReviewSubmissions", "relationships": {"build": {"data": {"type": "builds", "id": build["id"]}}}}})
            print("✓ Build soumis à la vérification bêta d'Apple (souvent < 24 h pour le premier)")
        except SystemExit:
            print("  soumission déjà faite ou refusée (voir TestFlight)")

    family = call("GET", f"/v1/betaGroups/{family['id']}")["data"]
    link = family["attributes"].get("publicLink")
    print(f"✓ Groupe interne prêt (installation immédiate via l'app TestFlight)")
    if link:
        print(f"✓ Lien public pour la famille : {link}")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
                summary.write(f"\n**Lien TestFlight famille** : {link}\n")


if __name__ == "__main__":
    main()

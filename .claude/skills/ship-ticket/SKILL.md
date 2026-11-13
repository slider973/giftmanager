---
name: ship-ticket
description: Orchestre le workflow complet d'un ticket GitHub — In Progress → plan → dev/design → review → PR → Done. Usage /ship-ticket <numéro-issue>. Utiliser quand l'utilisateur demande de traiter, développer ou livrer un ticket/issue.
argument-hint: <numéro-issue>
---

# Workflow de livraison d'un ticket

Ticket : **#$ARGUMENTS**. Tu es l'orchestrateur : tu délègues chaque étape à l'agent dédié et tu transmets à chacun le contexte utile (ticket, plan, rapports précédents). Les sous-agents ne se voient pas entre eux.

## 1. Démarrage — `ticket-manager`
Passe le ticket en **In Progress**, l'assigne, crée la branche. Récupère son rapport (corps du ticket, branche, ticket UI ou non).

## 2. Plan — `tech-planner`
Donne-lui le ticket complet. Récupère le plan (tâches logique / UI / tests / commandes).
S'il remonte des questions bloquantes, pose-les à l'utilisateur avant de continuer.

## 3. Implémentation
- Tâches logique → `developer` (avec le plan et le ticket).
- Tâches UI → `ui-designer` (avec le plan, le ticket, et le rapport du developer s'il a créé l'API/état dont l'UI dépend).
- Si les deux parts sont indépendantes, lance-les en parallèle ; sinon developer d'abord.
- Ignore l'agent dont la section du plan est « Aucune ».

## 4. Review — `code-reviewer`
Donne-lui le ticket et les rapports d'implémentation.
- `CHANGES_REQUESTED` → renvoie chaque point bloquant à l'agent indiqué, puis relance la review.
- Maximum 3 cycles ; au-delà, arrête-toi et présente les points restants à l'utilisateur.

## 5. PR — `pr-manager`
Uniquement après `APPROVE`. Il pousse, crée la PR avec `Closes #N`, puis passe le ticket en **Done**.

## 6. Mémoire — people-knowledge
Dès la PR ouverte (et de nouveau à sa fusion ou fermeture), mets à jour `projets/giftmanager/giftmanager.md`
dans `slider973/people-knowledge` en suivant son `skills/people-knowledge/SKILL.md` : voir la section
« Mémoire partagée » de `CLAUDE.md`. Ne délègue pas cette étape : elle demande le contexte de tout le ticket.

## Rapport final à l'utilisateur
- Ticket et statut sur le board
- URL de la PR
- Résumé des changements (logique + design)
- Résultat de la review et des tests
- Mémoire people-knowledge : commit poussé, ou pourquoi elle n'a pas pu être mise à jour
- Tout ce qui a échoué ou a été sauté

-- Budget privé (#40), rappels d'anniversaires (#43), suivi des prix et du stock (#39).
--   alice : parente du foyer A (enfant Léo, liste d'adulte « Alice »), eve : sa conjointe (même foyer)
--   bob   : parent du foyer B ; carol, dave : membres sans foyer ; frank : hors du groupe
begin;
select plan(50);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a2000000-0000-0000-0000-000000000001', 'alice3@test.local'),
  ('e2000000-0000-0000-0000-000000000001', 'eve3@test.local'),
  ('b2000000-0000-0000-0000-000000000001', 'bob3@test.local'),
  ('c2000000-0000-0000-0000-000000000001', 'carol3@test.local'),
  ('d2000000-0000-0000-0000-000000000001', 'dave3@test.local'),
  ('f2000000-0000-0000-0000-000000000001', 'frank3@test.local');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a2000000-0000-0000-0000-000000000001');
insert into ids values ('group', public.create_group('Famille V3', 'Foyer A', 'CH'));
insert into ids select 'household_a', hg.household_id from public.household_groups hg where hg.group_id = (select v from ids where k = 'group');
-- Léo : anniversaire dans 30 jours ; Alice (adulte) : dans 7 jours.
with x as (insert into public.children (household_id, first_name, birthdate)
           values ((select v from ids where k = 'household_a'), 'Léo', ((current_date + 30) - interval '8 years')::date) returning id)
  insert into ids select 'leo', id from x;
with x as (insert into public.children (household_id, first_name, birthdate, is_adult)
           values ((select v from ids where k = 'household_a'), 'Alice', ((current_date + 7) - interval '35 years')::date, true) returning id)
  insert into ids select 'alice_list', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Livre') returning id)
  insert into ids select 'livre', id from x;
with x as (insert into public.item_links (item_id, url, price, currency)
           values ((select v from ids where k = 'velo'), 'https://shop.example/velo', 100, 'CHF') returning id)
  insert into ids select 'velo_link', id from x;
with x as (insert into public.item_links (item_id, url, price, currency)
           values ((select v from ids where k = 'livre'), 'https://shop.example/livre', 20, 'CHF') returning id)
  insert into ids select 'livre_link', id from x;

select pg_temp.logout();
create temp table invite as select invite_code, (select invite_code from public.households where id = (select v from ids where k = 'household_a')) as household_code
  from public.groups where id = (select v from ids where k = 'group');
grant select on invite to authenticated;

select pg_temp.login('e2000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select public.join_household((select household_code from invite));
select pg_temp.login('b2000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select public.create_household((select v from ids where k = 'group'), 'Foyer B', 'FR');
select pg_temp.login('c2000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.login('d2000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.logout();

-- ---------------------------------------------------------------- #43 rappels
select is((select count(*) from public.events where kind = 'birthday' and child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a'))), 2::bigint, 'deux anniversaires créés automatiquement');
select is((select count(*) from private.birthday_reminder_candidates() where child_name = 'Léo' and days_before = 30), 3::bigint,
  'J-30 de Léo : bob, carol et dave');
select is((select count(*) from private.birthday_reminder_candidates() where child_name = 'Léo'
           and user_id in ('a2000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001')), 0::bigint,
  'jamais les parents de Léo');
select is((select count(*) from private.birthday_reminder_candidates() where child_name = 'Alice' and days_before = 7), 3::bigint,
  'J-7 de l''adulte : bob, carol et dave');
select is((select count(*) from private.birthday_reminder_candidates() where child_name = 'Alice'
           and user_id in ('a2000000-0000-0000-0000-000000000001', 'e2000000-0000-0000-0000-000000000001')), 0::bigint,
  'ni l''adulte concerné, ni son conjoint');
select is((select count(*) from private.birthday_reminder_candidates(current_date + 40) where event_id in (select id from public.events where child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a')))), 0::bigint,
  'événements passés : aucun rappel');
select is((select count(*) from private.birthday_reminder_candidates(current_date + 25)
           where child_name = 'Léo' and days_before = 7), 3::bigint,
  'rattrapage : à J-5 sans rappel envoyé, le rappel J-7 part (pas de J-30 en plus)');

update public.profiles set notify_birthday_reminders = false where id = 'd2000000-0000-0000-0000-000000000001';
select is((select count(*) from private.birthday_reminder_candidates() where user_id = 'd2000000-0000-0000-0000-000000000001'), 0::bigint,
  'préférence désactivée : dave n''est pas notifié');
select is((select count(*) from private.birthday_reminder_candidates() where event_id in (select id from public.events where child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a')))), 4::bigint, 'les autres le sont toujours');

select is((select count(*) from public.claim_birthday_reminders() where event_id in (select id from public.events where child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a')))), 4::bigint, 'claim : 4 rappels à envoyer');
select is((select count(*) from public.claim_birthday_reminders() where event_id in (select id from public.events where child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a')))), 0::bigint, 'claim idempotent : pas de rappel en double');
update public.profiles set notify_birthday_reminders = true where id = 'd2000000-0000-0000-0000-000000000001';
select is((select count(*) from public.claim_birthday_reminders() where event_id in (select id from public.events where child_id in (select id from public.children where household_id = (select v from ids where k = 'household_a')))), 2::bigint, 'réactivée : dave reçoit ses deux rappels, une seule fois');

select pg_temp.login('a2000000-0000-0000-0000-000000000001');
select throws_ok($$ select * from public.claim_birthday_reminders() $$, '42501', null, 'claim réservé au service role');
select lives_ok($$ update public.profiles set notify_birthday_reminders = false where id = auth.uid() $$,
  'chacun règle sa préférence dans son profil');
select is((select notify_birthday_reminders from public.profiles where id = auth.uid()), false, 'préférence enregistrée');

-- ---------------------------------------------------------------- #40 budget
select pg_temp.login('c2000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.set_budget((select v from ids where k = 'leo'), null, 200, 'chf') $$, 'carol fixe un budget pour Léo');
select lives_ok($$ select public.set_budget((select v from ids where k = 'leo'), null, 250, 'CHF') $$, 'mise à jour du même budget');
select is((select count(*) from public.budgets), 1::bigint, 'un seul budget par périmètre et devise');
select lives_ok($$ select public.set_budget((select v from ids where k = 'leo'), null, 50, 'EUR') $$, 'autre devise : autre budget');
select throws_ok($$ select public.set_budget(null, null, 50, 'CHF') $$, 'P0001', 'BUDGET_SCOPE_REQUIRED', 'périmètre obligatoire');
select public.reserve_item((select v from ids where k = 'velo'));
select is((select spent from public.my_budgets() where currency = 'CHF'), 100.00::numeric, 'dépensé : prix du cadeau réservé');
select is((select amount from public.my_budgets() where currency = 'CHF'), 250.00::numeric, 'montant du budget');
-- Comme l'app : paramètres nommés, enfant omis (budget d'événement seul).
select lives_ok($$ select public.set_budget(
    p_event => (select id from public.events where kind = 'christmas' limit 1),
    p_amount => 300, p_currency => 'EUR') $$, 'budget d''événement seul, enfant omis');
select throws_ok($$ select public.set_budget(p_child => (select v from ids where k = 'leo')) $$,
  'P0001', 'BUDGET_AMOUNT_REQUIRED', 'montant et devise obligatoires');

select pg_temp.login('a2000000-0000-0000-0000-000000000001');
select is((select count(*) from public.budgets), 0::bigint, 'le parent ne lit pas le budget d''un autre');
select is((select count(*) from public.my_budgets()), 0::bigint, 'my_budgets : seulement les siens');
with u as (update public.budgets set amount = 1 returning 1) select is((select count(*) from u), 0::bigint, 'modification du budget d''autrui : aucune ligne');
with d as (delete from public.budgets returning 1) select is((select count(*) from d), 0::bigint, 'suppression du budget d''autrui : aucune ligne');
select throws_ok($$ insert into public.budgets (user_id, child_id, amount, currency)
  values ('c2000000-0000-0000-0000-000000000001', (select v from ids where k = 'leo'), 10, 'CHF') $$,
  '42501', null, 'impossible de créer un budget au nom d''autrui');

select pg_temp.login('f2000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.set_budget((select v from ids where k = 'leo'), null, 10, 'CHF') $$, '42501', null,
  'hors du groupe : pas de budget sur un enfant');

-- ---------------------------------------------------------------- #39 prix et stock
select pg_temp.logout();
select is((select count(*) from public.links_to_check(1000) where link_id in (select v from ids where k like '%_link')), 1::bigint, 'seul le lien du cadeau réservé est relu');
select is((select user_id from public.record_price_check((select v from ids where k = 'velo_link'), 90, 'CHF', true)),
  'c2000000-0000-0000-0000-000000000001'::uuid, 'baisse de prix : notifie uniquement carol, l''auteur de la réservation');
select is((select count(*) from public.record_price_check((select v from ids where k = 'velo_link'), 90, 'CHF', true)), 0::bigint,
  'même prix relu : pas de notification en double');
select is((select count(*) from public.record_price_check((select v from ids where k = 'velo_link'), 95, 'CHF', true)), 0::bigint,
  'hausse : pas de notification');
select is((select new_price from public.record_price_check((select v from ids where k = 'velo_link'), 80, 'CHF', true)), 80.00::numeric,
  'nouvelle baisse : nouvelle notification');
select is((select count(*) from public.record_price_check((select v from ids where k = 'velo_link'), 70, 'EUR', true)), 0::bigint,
  'devise différente : pas de comparaison');
select is((select kind from public.record_price_check((select v from ids where k = 'velo_link'), null, null, false)), 'out_of_stock',
  'rupture de stock notifiée');
select is((select count(*) from public.record_price_check((select v from ids where k = 'velo_link'), null, null, false)), 0::bigint,
  'rupture déjà notifiée : pas de doublon');
select is((select count(*) from public.record_price_check((select v from ids where k = 'velo_link'), null, null, null)), 0::bigint,
  'relecture sans information : ignorée');
select lives_ok($$ select * from public.record_price_check((select v from ids where k = 'velo_link'), 75, 'XYZ1', true) $$,
  'devise invalide : ignorée sans erreur');
select lives_ok($$ select * from public.record_price_check((select v from ids where k = 'velo_link'), 1e12, 'CHF', true) $$,
  'prix hors plage : ignoré sans erreur');
select is((select count(*) from public.link_checks where link_id = (select v from ids where k = 'velo_link')), 1::bigint,
  'dernière tentative enregistrée pour la rotation');
select is((select count(*) from public.record_price_check((select v from ids where k = 'livre_link'), 5, 'CHF', true)), 0::bigint,
  'cadeau non réservé : jamais de notification');

select pg_temp.login('a2000000-0000-0000-0000-000000000001');
select is((select count(*) from public.price_history), 0::bigint, 'parent : historique de prix illisible');
select is((select count(*) from public.my_price_history((select v from ids where k = 'velo'))), 0::bigint, 'parent : my_price_history vide');
select throws_ok($$ select * from public.link_checks $$, '42501', null, 'parent : link_checks illisible');
select throws_ok($$ select * from public.record_price_check((select v from ids where k = 'velo_link'), 1, 'CHF', true) $$, '42501', null,
  'parent : pas d''accès à la fonction d''enregistrement');
select pg_temp.login('d2000000-0000-0000-0000-000000000001');
select is((select count(*) from public.price_history), 0::bigint, 'membre non réservant : historique illisible');
select pg_temp.login('c2000000-0000-0000-0000-000000000001');
select cmp_ok((select count(*) from public.my_price_history((select v from ids where k = 'velo'))), '>=', 5::bigint,
  'carol lit l''historique de son cadeau');
select public.set_reservation_purchased((select v from ids where k = 'velo'));
select pg_temp.logout();
select is((select count(*) from public.links_to_check(1000) where link_id in (select v from ids where k like '%_link')), 0::bigint, 'cadeau acheté : plus relu');

select * from finish();
rollback;

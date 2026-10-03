-- Cagnotte (#35), listes d'adultes (#37), équilibre (#41), remerciements (#42), anniversaires (#43).
--   alice : parente du foyer A (enfant Léo, et liste adulte « Alice »)
--   bob   : parent du foyer B
--   carol, dave : membres sans foyer (grands-parents)
begin;
select plan(31);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a1000000-0000-0000-0000-000000000001', 'alice2@test.local'),
  ('b1000000-0000-0000-0000-000000000001', 'bob2@test.local'),
  ('c1000000-0000-0000-0000-000000000001', 'carol2@test.local'),
  ('d1000000-0000-0000-0000-000000000001', 'dave2@test.local');
update public.profiles set display_name = 'Carol' where id = 'c1000000-0000-0000-0000-000000000001';
update public.profiles set display_name = 'Dave' where id = 'd1000000-0000-0000-0000-000000000001';

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a1000000-0000-0000-0000-000000000001');
insert into ids values ('group', public.create_group('Famille V2', 'Foyer A', 'CH'));
insert into ids select 'household_a', id from public.households;
with x as (insert into public.children (household_id, first_name, birthdate)
           values ((select v from ids where k = 'household_a'), 'Léo', (current_date - interval '8 years' + interval '10 days')::date) returning id)
  insert into ids select 'leo', id from x;
with x as (insert into public.children (household_id, first_name, is_adult)
           values ((select v from ids where k = 'household_a'), 'Alice', true) returning id)
  insert into ids select 'alice_list', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Livre') returning id)
  insert into ids select 'livre', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'alice_list'), 'Parfum') returning id)
  insert into ids select 'parfum', id from x;

-- #43 anniversaire créé automatiquement à l'ajout d'un enfant avec date de naissance
select is((select count(*) from public.events where kind = 'birthday' and child_id = (select v from ids where k = 'leo')),
  1::bigint, 'anniversaire de Léo créé automatiquement');
select is((select event_date from public.events where kind = 'birthday' and child_id = (select v from ids where k = 'leo')),
  (current_date + interval '10 days')::date, 'à la bonne date (prochain anniversaire)');
select lives_ok($$ select public.ensure_birthday_events((select v from ids where k = 'group')) $$, 'ensure_birthday_events idempotent');
select is((select count(*) from public.events where kind = 'birthday'), 1::bigint, 'pas de doublon d''anniversaire');

select pg_temp.logout();
create temp table invite as select invite_code from public.groups where id = (select v from ids where k = 'group');
grant select on invite to authenticated;

select pg_temp.login('b1000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select public.create_household((select v from ids where k = 'group'), 'Foyer B', 'FR');
select pg_temp.login('c1000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.login('d1000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));

-- ---------------------------------------------------------------- #35 cagnotte
select pg_temp.login('c1000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.join_pot((select v from ids where k = 'velo'), 50, 'CHF') $$, 'carol lance une cagnotte');
select pg_temp.login('d1000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.join_pot((select v from ids where k = 'velo'), 30, 'EUR') $$, 'P0001', 'CURRENCY_MISMATCH',
  'même devise pour toute la cagnotte');
select lives_ok($$ select public.join_pot((select v from ids where k = 'velo'), 30, 'CHF') $$, 'dave participe');
select is((select count(*) from public.pot_participants((select v from ids where k = 'velo'))), 2::bigint,
  'un participant voit les autres participants');
select is((select status from public.child_items((select v from ids where k = 'leo')) where title = 'Vélo'), 'pot', 'statut « pot »');
select is((select pot_total from public.child_items((select v from ids where k = 'leo')) where title = 'Vélo'), 80.00::numeric,
  'progression de la cagnotte');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'velo')) $$, 'P0001', 'ITEM_UNAVAILABLE',
  'un cadeau en cagnotte ne se réserve pas seul');

select pg_temp.login('b1000000-0000-0000-0000-000000000001');
select is((select pot_total from public.child_items((select v from ids where k = 'leo')) where title = 'Vélo'), 80.00::numeric,
  'un non-participant voit la progression');
select is((select count(*) from public.pot_participants((select v from ids where k = 'velo'))), 0::bigint,
  'un non-participant ne voit pas qui participe');
select is((select count(*) from public.contributions), 0::bigint, 'un non-participant ne lit aucune participation');
select lives_ok($$ select public.reserve_item((select v from ids where k = 'livre')) $$, 'bob réserve le livre');
select pg_temp.login('c1000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.join_pot((select v from ids where k = 'livre'), 10, 'CHF') $$, 'P0001', 'ITEM_UNAVAILABLE',
  'un cadeau réservé ne passe pas en cagnotte');

select pg_temp.login('a1000000-0000-0000-0000-000000000001');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))
           where status is not null or pot_total is not null or pot_count is not null), 0::bigint,
  'parent : ni statut ni progression de cagnotte');
select is((select count(*) from public.pot_participants((select v from ids where k = 'velo'))), 0::bigint,
  'parent : aucun participant');
select is((select count(*) from public.contributions), 0::bigint, 'parent : aucune participation lisible');
select throws_ok($$ select public.join_pot((select v from ids where k = 'velo'), 10, 'CHF') $$, 'P0001', 'ITEM_UNAVAILABLE',
  'parent : ne peut pas participer');

-- ---------------------------------------------------------------- #37 listes d'adultes
select pg_temp.login('c1000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.reserve_item((select v from ids where k = 'parfum')) $$, 'carol réserve le parfum d''Alice');
select pg_temp.login('a1000000-0000-0000-0000-000000000001');
select is((select status from public.child_items((select v from ids where k = 'alice_list')) where title = 'Parfum'), null,
  'Alice ne voit pas de statut sur sa propre liste');

-- ---------------------------------------------------------------- #41 équilibre
select is((select count(*) from public.children_reservation_counts() where child_id in
           (select v from ids where k in ('leo', 'alice_list'))), 0::bigint, 'parent : aucun compteur pour son foyer');
select pg_temp.login('b1000000-0000-0000-0000-000000000001');
select is((select reserved_count from public.children_reservation_counts() where child_id = (select v from ids where k = 'leo')),
  1, 'bob voit 1 cadeau réservé pour Léo');
select is((select pot_count from public.children_reservation_counts() where child_id = (select v from ids where k = 'leo')),
  1, 'et 1 cagnotte');

-- ---------------------------------------------------------------- #42 remerciements
select pg_temp.login('a1000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'Merci pour le vélo !') $$, 'alice remercie');
select is((select count(*) from public.item_donors((select v from ids where k = 'velo'))), 0::bigint,
  'parent : aucun donateur révélé par défaut');
select pg_temp.login('b1000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'x') $$, 'P0001', 'ITEM_NOT_FOUND',
  'seul un parent remercie');
select is((select count(*) from public.my_thanks()), 0::bigint, 'bob (non donateur) ne lit pas le remerciement');
select pg_temp.login('c1000000-0000-0000-0000-000000000001');
select is((select count(*) from public.my_thanks()), 1::bigint, 'carol (participante) reçoit le remerciement');
select public.reveal_myself((select v from ids where k = 'velo'));
select pg_temp.login('a1000000-0000-0000-0000-000000000001');
select is((select display_name from public.item_donors((select v from ids where k = 'velo'))), 'Carol',
  'après levée volontaire, le parent voit le donateur (et lui seul)');

select * from finish();
rollback;

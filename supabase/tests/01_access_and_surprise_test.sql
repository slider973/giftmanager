-- Règles d'accès (#3), réservation anonyme et mode surprise (#4), idées (#17).
--
-- Famille de test :
--   alice, paul : parents du foyer A (CH), enfant Léo
--   bob         : parent du foyer B (FR), enfant Emma
--   carol       : grand-mère, membre du groupe sans foyer
--   dave        : étranger au groupe
begin;
select plan(49);

-- Aides : se connecter en tant qu'un utilisateur / revenir en superutilisateur.
create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a0000000-0000-0000-0000-000000000001', 'alice@test.local'),
  ('a0000000-0000-0000-0000-000000000002', 'paul@test.local'),
  ('b0000000-0000-0000-0000-000000000001', 'bob@test.local'),
  ('c0000000-0000-0000-0000-000000000001', 'carol@test.local'),
  ('d0000000-0000-0000-0000-000000000001', 'dave@test.local');

select is((select count(*) from public.profiles where id in (select id from auth.users where email like '%@test.local')), 5::bigint,
  'un profil est créé à chaque inscription');

-- alice crée le groupe (foyer A + Noël)
select pg_temp.login('a0000000-0000-0000-0000-000000000001');
create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;
insert into ids values ('group', public.create_group('Famille Test', 'Foyer A', 'CH'));
insert into ids select 'household_a', id from public.households;
select is((select count(*) from public.events where kind = 'christmas'), 1::bigint, 'Noël est créé avec le groupe');
with x as (insert into public.children (household_id, first_name) values ((select v from ids where k = 'household_a'), 'Léo') returning id) insert into ids select 'leo', id from x;
select pg_temp.logout();
create temp table invite as select invite_code from public.groups where created_by = 'a0000000-0000-0000-0000-000000000001';
grant select on invite to authenticated;
create temp table household_invite as select invite_code from public.households where name = 'Foyer A';
grant select on household_invite to authenticated;

-- bob rejoint et crée le foyer B ; carol rejoint ; paul rejoint le foyer A
select pg_temp.login('b0000000-0000-0000-0000-000000000001');
select is(public.join_group((select invite_code from invite)), (select v from ids where k = 'group'), 'join_group avec le bon code');
insert into ids values ('household_b', public.create_household((select v from ids where k = 'group'), 'Foyer B', 'FR'));
with x as (insert into public.children (household_id, first_name) values ((select v from ids where k = 'household_b'), 'Emma') returning id) insert into ids select 'emma', id from x;
select throws_ok($$ select public.join_group('ZZZZZZ') $$, 'P0001', 'INVALID_INVITE_CODE', 'code d''invitation invalide refusé');

select pg_temp.login('c0000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.join_group((select invite_code from invite)) $$, 'carol rejoint le groupe');

select pg_temp.login('a0000000-0000-0000-0000-000000000002');
select lives_ok($$ select public.join_group((select invite_code from invite)) $$, 'paul rejoint le groupe');
select is(public.join_household((select invite_code from household_invite)), (select v from ids where k = 'household_a'),
  'paul rejoint le foyer A avec le code du foyer');

-- Liste de Léo (souhaits ajoutés par alice)
select pg_temp.login('a0000000-0000-0000-0000-000000000001');
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Lego') returning id) insert into ids select 'lego', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'PS5') returning id) insert into ids select 'ps5', id from x;
with x as (insert into public.wish_items (child_id, title, owned) values ((select v from ids where k = 'leo'), 'Vélo', true) returning id) insert into ids select 'velo', id from x;
insert into public.item_links (item_id, url, store, country, price, currency)
  values ((select v from ids where k = 'lego'), 'https://www.galaxus.ch/lego', 'Galaxus', 'CH', 199, 'CHF');
select throws_ok($$
  insert into public.wish_items (child_id, kind, title) values ((select v from ids where k = 'leo'), 'idea', 'Idée de parent')
$$, '42501', null, 'un parent ne peut pas proposer d''idée pour son enfant');

-- ---------------------------------------------------------------- #3 accès
select pg_temp.login('d0000000-0000-0000-0000-000000000001');
select is((select count(*) from public.groups), 0::bigint, 'étranger : aucun groupe visible');
select is((select count(*) from public.children), 0::bigint, 'étranger : aucun enfant visible');
select is((select count(*) from public.wish_items), 0::bigint, 'étranger : aucun cadeau visible');
select is((select count(*) from public.item_links), 0::bigint, 'étranger : aucun lien visible');
select is((select count(*) from public.profiles where id <> auth.uid()), 0::bigint, 'étranger : aucun profil de la famille visible');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'lego')) $$, 'P0001', 'ITEM_NOT_FOUND', 'étranger : réservation impossible');

select pg_temp.login('b0000000-0000-0000-0000-000000000001');
select is((select count(*) from public.children), 2::bigint, 'membre : voit les enfants du groupe');
select is((select count(*) from public.profiles), 4::bigint, 'membre : voit les profils de sa famille');
select throws_ok($$
  insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Intrus')
$$, '42501', null, 'non-parent : ne peut pas ajouter de souhait à Léo');
update public.children set first_name = 'X' where id = (select v from ids where k = 'leo');
select is((select first_name from public.children where id = (select v from ids where k = 'leo')), 'Léo',
  'non-parent : ne peut pas modifier Léo');
update public.wish_items set title = 'X' where id = (select v from ids where k = 'lego');
select is((select title from public.wish_items where id = (select v from ids where k = 'lego')), 'Lego',
  'non-parent : ne peut pas modifier un souhait de Léo');
delete from public.children where id = (select v from ids where k = 'leo');
select is((select count(*) from public.children where id = (select v from ids where k = 'leo')), 1::bigint,
  'non-parent : ne peut pas supprimer Léo');

-- ---------------------------------------------------------------- #17 idées
select pg_temp.login('c0000000-0000-0000-0000-000000000001');
with x as (insert into public.wish_items (child_id, kind, title) values ((select v from ids where k = 'leo'), 'idea', 'Livre') returning id) insert into ids select 'livre', id from x;
select pg_temp.login('b0000000-0000-0000-0000-000000000001');
select is((select count(*) from public.wish_items where kind = 'idea'), 1::bigint, 'membre : voit l''idée proposée pour Léo');
select pg_temp.login('a0000000-0000-0000-0000-000000000001');
select is((select count(*) from public.wish_items where kind = 'idea'), 0::bigint, 'parent (alice) : ne voit aucune idée pour son enfant');
select is((select count(*) from public.child_items((select v from ids where k = 'leo')) where kind = 'idea'), 0::bigint,
  'parent : child_items ne renvoie aucune idée');
select pg_temp.login('a0000000-0000-0000-0000-000000000002');
select is((select count(*) from public.wish_items where kind = 'idea'), 0::bigint, 'parent (paul) : ne voit aucune idée pour son enfant');

-- ---------------------------------------------------------------- #4 réservations
select pg_temp.login('c0000000-0000-0000-0000-000000000001');
select is(public.reserve_item((select v from ids where k = 'lego')), 'reserved', 'carol réserve le Lego');
select is(public.item_public_status((select v from ids where k = 'lego')), 'mine', 'carol voit « mine »');
select is(public.reserve_item((select v from ids where k = 'livre')), 'reserved', 'une idée se réserve comme un souhait');

select pg_temp.login('b0000000-0000-0000-0000-000000000001');
select is(public.item_public_status((select v from ids where k = 'lego')), 'taken', 'bob voit « taken »');
select is((select count(*) from public.reservations), 0::bigint, 'bob ne lit aucune réservation d''autrui');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'lego')) $$, 'P0001', 'ITEM_UNAVAILABLE', 'bob ne peut pas réserver un cadeau pris');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'velo')) $$, 'P0001', 'ITEM_OWNED', 'un cadeau possédé ne se réserve pas');
select is((select status from public.child_items((select v from ids where k = 'leo')) where title = 'Lego'), 'taken',
  'child_items : bob voit le Lego pris');

select pg_temp.login('a0000000-0000-0000-0000-000000000001');
select is(public.item_public_status((select v from ids where k = 'lego')), null, 'parent : aucun statut (mode surprise)');
select is((select count(*) from public.child_items((select v from ids where k = 'leo')) where status is not null), 0::bigint,
  'parent : child_items ne renvoie aucun statut');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'lego')) $$, 'P0001', 'ITEM_UNAVAILABLE',
  'parent (Père Noël) : « plus disponible », sans identité');
select is(public.reserve_item((select v from ids where k = 'ps5')), 'reserved', 'parent : peut réserver un cadeau libre de son enfant');
select is((select count(*) from public.my_reservations()), 1::bigint, 'parent : retrouve sa réservation dans Mes achats');

select pg_temp.login('c0000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.cancel_reservation((select v from ids where k = 'lego')) $$, 'carol annule sa réservation');
select pg_temp.login('b0000000-0000-0000-0000-000000000001');
select is(public.item_public_status((select v from ids where k = 'lego')), 'available', 'le Lego redevient disponible');
select throws_ok($$ select public.cancel_reservation((select v from ids where k = 'ps5')) $$, 'P0001', 'RESERVATION_NOT_FOUND',
  'on ne peut pas annuler la réservation d''un autre');

select pg_temp.logout();
select throws_ok($$
  insert into public.reservations (item_id, user_id) values
    ((select v from ids where k = 'ps5'), 'b0000000-0000-0000-0000-000000000001')
$$, '23505', null, 'une seule réservation par cadeau (contrainte d''unicité)');

-- ---------------------------------------------------------------- revue de sécurité
select pg_temp.login('c0000000-0000-0000-0000-000000000001');
select throws_ok($$
  insert into public.household_members (household_id, user_id)
  values ((select v from ids where k = 'household_a'), auth.uid())
$$, '42501', null, 'un membre ne peut pas s''ajouter lui-même à un foyer');
select throws_ok($$ select public.join_household('ZZZZZZZZ') $$, 'P0001', 'INVALID_INVITE_CODE', 'code de foyer invalide refusé');

select pg_temp.login('a0000000-0000-0000-0000-000000000001');
delete from public.household_members where user_id = auth.uid();
select ok(private.is_parent_of((select v from ids where k = 'leo')), 'un parent ne peut pas quitter son foyer pour lever le mode surprise');
select throws_ok($$ select public.join_household((select invite_code from household_invite)) $$, 'P0001', 'ALREADY_IN_HOUSEHOLD',
  'un parent ne peut pas rejoindre un second foyer');
select throws_ok($$ update public.groups set invite_code = 'HACKED' where id = (select v from ids where k = 'group') $$,
  '42501', null, 'le code d''invitation du groupe n''est pas modifiable');

select pg_temp.login('d0000000-0000-0000-0000-000000000001');
insert into ids values ('other_group', public.create_group('Autre famille', 'Foyer D', 'FR'));
with x as (insert into public.events (group_id, kind, title, event_date)
           values ((select v from ids where k = 'other_group'), 'other', 'Fête des voisins', current_date + 60) returning id)
  insert into ids select 'other_event', id from x;
select pg_temp.login('a0000000-0000-0000-0000-000000000001');
select throws_ok($$
  insert into public.wish_items (child_id, event_id, title)
  values ((select v from ids where k = 'leo'), (select v from ids where k = 'other_event'), 'Hors groupe')
$$, 'P0001', 'INVALID_EVENT', 'un cadeau ne peut pas viser l''événement d''un autre groupe');

select pg_temp.logout();
select hasnt_function('public', 'is_parent_of', array['uuid'], 'les fonctions d''aide ne sont pas exposées en RPC');
select is(has_table_privilege('anon', 'public.wish_items', 'select'), false, 'anon n''a aucun droit sur les tables');

select * from finish();
rollback;

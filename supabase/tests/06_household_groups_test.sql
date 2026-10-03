-- #60 Foyer partagé entre plusieurs familles.
--   alice : parente du foyer A (enfant Léo). Elle appartient à deux familles : Lazzarotto et Belle-famille.
--   bob   : membre de Lazzarotto uniquement. carol : membre de Belle-famille uniquement.
--   eve   : seconde parente du foyer A (rejoint par code de foyer).
begin;
select plan(25);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a6000000-0000-0000-0000-000000000001', 'alice6@test.local'),
  ('b6000000-0000-0000-0000-000000000001', 'bob6@test.local'),
  ('c6000000-0000-0000-0000-000000000001', 'carol6@test.local'),
  ('e6000000-0000-0000-0000-000000000001', 'eve6@test.local');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

-- ---------------------------------------------------------------- mise en place
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
insert into ids values ('g1', public.create_group('Lazzarotto', 'Foyer d''Alice', 'CH'));
insert into ids select 'household_a', household_id from public.household_members where user_id = auth.uid();
with x as (insert into public.children (household_id, first_name, birthdate)
           values ((select v from ids where k = 'household_a'), 'Léo', '2018-03-12') returning id)
  insert into ids select 'leo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;

-- Bob crée la seconde famille et invite Alice.
select pg_temp.login('b6000000-0000-0000-0000-000000000001');
insert into ids values ('g2', public.create_group('Belle-famille', 'Foyer de Bob', 'FR'));
create temp table invite2 on commit drop as
  select invite_code from public.groups where id = (select v from ids where k = 'g2');
grant all on invite2 to authenticated;

-- ---------------------------------------------------------------- rejoindre sans recréer ses enfants
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.join_group((select invite_code from invite2)) $$, 'alice rejoint la seconde famille');
select is((select count(*) from public.household_members where user_id = auth.uid()), 1::bigint,
  'un seul foyer, toutes familles confondues');
select is((select count(*) from public.household_groups where household_id = (select v from ids where k = 'household_a')), 2::bigint,
  'son foyer est partagé avec les deux familles');
select is((select count(*) from public.children where household_id = (select v from ids where k = 'household_a')), 1::bigint,
  'Léo n''existe qu''une fois : aucun enfant à recréer');

-- ---------------------------------------------------------------- visibilité croisée
select pg_temp.login('b6000000-0000-0000-0000-000000000001');
select ok(private.can_view_child((select v from ids where k = 'leo')), 'bob (famille 2) voit Léo');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))), 1::bigint,
  'bob voit la liste de Léo');

select pg_temp.login('c6000000-0000-0000-0000-000000000001');
select ok(not private.can_view_child((select v from ids where k = 'leo')), 'carol, hors des deux familles, ne voit pas Léo');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))), 0::bigint,
  'et ne voit aucun de ses cadeaux');

-- ---------------------------------------------------------------- anti-doublon entre familles
select pg_temp.login('b6000000-0000-0000-0000-000000000001');
select is(public.reserve_item((select v from ids where k = 'velo')), 'reserved', 'bob réserve le vélo depuis la famille 2');

-- carol rejoint la famille 1 : elle doit voir le vélo « pris », sans savoir par qui.
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
create temp table invite1 on commit drop as
  select invite_code from public.groups where id = (select v from ids where k = 'g1');
grant all on invite1 to authenticated;
select pg_temp.login('c6000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite1));
select is((select status from public.child_items((select v from ids where k = 'leo'))), 'taken',
  'réservation faite dans une famille : « pris » dans l''autre (anti-doublon)');
select is((select my_reservation from public.child_items((select v from ids where k = 'leo'))), null,
  'carol ne voit pas la réservation de bob');
select throws_ok($$ select public.reserve_item((select v from ids where k = 'velo')) $$, 'P0001', 'ITEM_UNAVAILABLE',
  'impossible de réserver deux fois le même cadeau depuis deux familles');
select is((select count(*) from public.reservations), 0::bigint,
  'carol ne lit aucune réservation : seul son auteur voit la sienne');

-- ---------------------------------------------------------------- mode surprise préservé
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
select is((select status from public.child_items((select v from ids where k = 'leo'))), null,
  'la mère de Léo ne voit aucun statut, dans aucune famille');
select is((select count(*) from public.reservations), 0::bigint, 'et ne lit aucune réservation');

-- ---------------------------------------------------------------- second parent
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
create temp table hinvite on commit drop as
  select invite_code from public.households where id = (select v from ids where k = 'household_a');
grant all on hinvite to authenticated;
select pg_temp.login('e6000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite1));
select lives_ok($$ select public.join_household((select invite_code from hinvite)) $$, 'eve rejoint le foyer d''Alice');
select ok(private.is_parent_of((select v from ids where k = 'leo')), 'eve est parente de Léo');
select is((select status from public.child_items((select v from ids where k = 'leo'))), null,
  'la seconde parente non plus ne voit aucun statut');

-- ---------------------------------------------------------------- retrait du partage
select pg_temp.login('a6000000-0000-0000-0000-000000000001');
select lives_ok($$ select public.unshare_household_from_group((select v from ids where k = 'g2')) $$,
  'alice retire son foyer de la seconde famille');
select pg_temp.login('b6000000-0000-0000-0000-000000000001');
select ok(not private.can_view_child((select v from ids where k = 'leo')), 'bob ne voit plus Léo');
select is((select count(*) from public.reservations where user_id = auth.uid()), 1::bigint,
  'sa réservation est conservée');

-- ---------------------------------------------------------------- événements
select pg_temp.logout();
select is((select count(*) from public.events where kind = 'christmas'), 1::bigint,
  'un seul Noël pour toute l''application');
select is((select count(*) from public.events where kind = 'birthday' and child_id = (select v from ids where k = 'leo')), 1::bigint,
  'un seul anniversaire par enfant, quel que soit le nombre de familles');
select is((select group_id from public.events where kind = 'christmas'), null, 'Noël n''appartient à aucun groupe');

select pg_temp.login('a6000000-0000-0000-0000-000000000001');
select throws_ok($$
  insert into public.household_groups (household_id, group_id)
  values ((select v from ids where k = 'household_a'), (select v from ids where k = 'g2'))
$$, '42501', null, 'le partage ne s''écrit pas directement : il passe par la fonction');

select * from finish();
rollback;

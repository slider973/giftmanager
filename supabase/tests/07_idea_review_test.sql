-- #57 Les idées sont soumises aux parents au lieu de leur être cachées.
--   alice : parente du foyer A (enfant Léo). bob : membre de la famille, sans lien avec Léo.
--   carol : autre membre. dave : hors de la famille.
begin;
select plan(28);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a7000000-0000-0000-0000-000000000001', 'alice7@test.local'),
  ('b7000000-0000-0000-0000-000000000001', 'bob7@test.local'),
  ('c7000000-0000-0000-0000-000000000001', 'carol7@test.local'),
  ('d7000000-0000-0000-0000-000000000001', 'dave7@test.local');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a7000000-0000-0000-0000-000000000001');
insert into ids values ('g', public.create_group('Famille Idées', 'Foyer d''Alice', 'CH'));
insert into ids select 'household_a', household_id from public.household_members where user_id = auth.uid();
with x as (insert into public.children (household_id, first_name, birthdate)
           values ((select v from ids where k = 'household_a'), 'Léo', '2018-03-12') returning id)
  insert into ids select 'leo', id from x;
create temp table invite on commit drop as
  select invite_code from public.groups where id = (select v from ids where k = 'g');
grant all on invite to authenticated;

select pg_temp.login('b7000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.login('c7000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));

-- ---------------------------------------------------------------- audience
select pg_temp.login('b7000000-0000-0000-0000-000000000001');
select is(public.idea_audience((select v from ids where k = 'leo')), 1,
  'une idée non soumise n''atteindrait qu''une personne : carol');

-- ---------------------------------------------------------------- idée soumise
with x as (insert into public.wish_items (child_id, kind, title, review_status)
           values ((select v from ids where k = 'leo'), 'idea', 'Lego Technic', 'pending') returning id)
  insert into ids select 'lego', id from x;
select is((select review_status from public.wish_items where id = (select v from ids where k = 'lego')), 'pending',
  'l''idée est en attente de validation');

select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select is((select count(*) from public.pending_ideas()), 1::bigint, 'la mère de Léo voit l''idée soumise');
select is((select child_name from public.pending_ideas()), 'Léo', 'avec le prénom de l''enfant');
select is((select author_name from public.pending_ideas()), (select display_name from public.profiles where id = 'b7000000-0000-0000-0000-000000000001'),
  'et le nom de celui qui propose');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))), 1::bigint,
  'l''idée soumise apparaît dans la liste de l''enfant pour sa mère');
select is((select status from public.child_items((select v from ids where k = 'leo'))), null,
  'mais aucun statut de réservation : le mode surprise tient');

-- ---------------------------------------------------------------- idée non soumise
select pg_temp.login('b7000000-0000-0000-0000-000000000001');
with x as (insert into public.wish_items (child_id, kind, title, review_status)
           values ((select v from ids where k = 'leo'), 'idea', 'Surprise totale', 'none') returning id)
  insert into ids select 'secret', id from x;
select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))), 1::bigint,
  'l''idée non soumise reste invisible à la mère (ancien comportement conservé)');
select pg_temp.login('c7000000-0000-0000-0000-000000000001');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'))), 2::bigint,
  'carol, elle, voit les deux idées');

-- ---------------------------------------------------------------- verdict réservé aux parents
select pg_temp.login('c7000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.review_idea((select v from ids where k = 'lego'), 'accepted') $$,
  'P0001', 'NOT_A_PARENT', 'un non-parent ne tranche pas');
select pg_temp.login('d7000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.review_idea((select v from ids where k = 'lego'), 'accepted') $$,
  'P0001', 'ITEM_NOT_FOUND', 'hors de la famille : le cadeau n''existe pas');

select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.review_idea((select v from ids where k = 'lego'), 'peut-être') $$,
  'P0001', 'INVALID_DECISION', 'décision inconnue refusée');
select throws_ok($$ select public.review_idea((select v from ids where k = 'secret'), 'accepted') $$,
  'P0001', 'IDEA_NOT_SUBMITTED', 'une idée non soumise ne se valide pas');

-- ---------------------------------------------------------------- acceptation
select is(public.review_idea((select v from ids where k = 'lego'), 'accepted', 'Bonne idée !'), 'accepted',
  'la mère accepte l''idée');
select is((select kind from public.wish_items where id = (select v from ids where k = 'lego')), 'wish',
  'l''idée rejoint la liste de souhaits');
select is((select created_by from public.wish_items where id = (select v from ids where k = 'lego')),
  'b7000000-0000-0000-0000-000000000001', 'l''auteur reste crédité');
select is((select reviewed_by from public.wish_items where id = (select v from ids where k = 'lego')),
  'a7000000-0000-0000-0000-000000000001', 'le verdict est tracé');
select is((select review_note from public.wish_items where id = (select v from ids where k = 'lego')), 'Bonne idée !',
  'le mot du parent est conservé');
select is((select count(*) from public.pending_ideas()), 0::bigint, 'plus rien en attente');

-- Le cadeau accepté est réservable, et la mère n'en voit toujours pas le statut.
select pg_temp.login('c7000000-0000-0000-0000-000000000001');
select is(public.reserve_item((select v from ids where k = 'lego')), 'reserved', 'carol réserve le cadeau accepté');
select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select is((select status from public.child_items((select v from ids where k = 'leo'), p_kind => 'wish')), null,
  'la mère ne voit pas que le cadeau accepté est réservé');

-- ---------------------------------------------------------------- refus
select pg_temp.login('b7000000-0000-0000-0000-000000000001');
with x as (insert into public.wish_items (child_id, kind, title, review_status)
           values ((select v from ids where k = 'leo'), 'idea', 'Trottinette', 'pending') returning id)
  insert into ids select 'trot', id from x;
select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select is(public.review_idea((select v from ids where k = 'trot'), 'rejected', 'Trop dangereux'), 'rejected',
  'la mère refuse une idée');
select is((select kind from public.wish_items where id = (select v from ids where k = 'trot')), 'idea',
  'une idée refusée ne devient pas un souhait');
select pg_temp.login('c7000000-0000-0000-0000-000000000001');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'), p_kind => 'idea')), 1::bigint,
  'carol ne voit plus l''idée refusée (seule l''idée secrète reste)');
select pg_temp.login('b7000000-0000-0000-0000-000000000001');
select is((select count(*) from public.child_items((select v from ids where k = 'leo'), p_kind => 'idea')
           where review_status = 'rejected'), 1::bigint, 'son auteur voit le refus');

-- ---------------------------------------------------------------- « il l'a déjà »
with x as (insert into public.wish_items (child_id, kind, title, review_status)
           values ((select v from ids where k = 'leo'), 'idea', 'Ballon', 'pending') returning id)
  insert into ids select 'ballon', id from x;
select pg_temp.login('a7000000-0000-0000-0000-000000000001');
select is(public.review_idea((select v from ids where k = 'ballon'), 'owned_already'), 'owned_already',
  'la mère signale que Léo l''a déjà');
select ok((select owned from public.wish_items where id = (select v from ids where k = 'ballon')),
  'le cadeau est marqué comme possédé, ce qui évite les doublons');

-- ---------------------------------------------------------------- écriture directe interdite
select pg_temp.login('b7000000-0000-0000-0000-000000000001');
select throws_ok($$
  update public.wish_items set review_status = 'accepted' where id = (select v from ids where k = 'secret')
$$, '42501', null, 'l''auteur ne valide pas sa propre idée');

select * from finish();
rollback;

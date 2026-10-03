-- Corrections de revue v2 : anniversaires idempotents et mis à jour (#43), lecture des cagnottes et
-- remerciements par un participant devenu parent, URL des photos de remerciement.
--   alice : parente de Léo ; carol, dave : membres (participants à la cagnotte)
begin;
select plan(13);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a3000000-0000-0000-0000-000000000001', 'alice5@test.local'),
  ('c3000000-0000-0000-0000-000000000001', 'carol5@test.local'),
  ('d3000000-0000-0000-0000-000000000001', 'dave5@test.local');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a3000000-0000-0000-0000-000000000001');
insert into ids values ('group', public.create_group('Famille V5', 'Foyer A', 'CH'));
insert into ids select 'household_a', id from public.households where group_id = (select v from ids where k = 'group');
with x as (insert into public.children (household_id, first_name, birthdate)
           values ((select v from ids where k = 'household_a'), 'Léo', ((current_date + 20) - interval '8 years')::date) returning id)
  insert into ids select 'leo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;

select pg_temp.logout();
create temp table invite as select invite_code, (select invite_code from public.households where id = (select v from ids where k = 'household_a')) as household_code
  from public.groups where id = (select v from ids where k = 'group');
grant select on invite to authenticated;
select pg_temp.login('c3000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.login('d3000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));

-- ---------------------------------------------------------------- #43 anniversaires
select pg_temp.login('a3000000-0000-0000-0000-000000000001');
select is((select count(*) from public.events where kind = 'birthday'), 1::bigint, 'un anniversaire créé à l''ajout de l''enfant');
select public.ensure_birthday_events((select v from ids where k = 'group'));
select public.ensure_birthday_events((select v from ids where k = 'group'));
select is((select count(*) from public.events where kind = 'birthday'), 1::bigint, 'plusieurs appels : toujours un seul événement');

update public.children set birthdate = ((current_date + 40) - interval '8 years')::date where id = (select v from ids where k = 'leo');
select is((select count(*) from public.events where kind = 'birthday'), 1::bigint, 'changement de date : pas de doublon');
select is((select event_date from public.events where kind = 'birthday'), current_date + 40, 'changement de date : événement futur mis à jour');

update public.children set first_name = 'Léon' where id = (select v from ids where k = 'leo');
select is((select title from public.events where kind = 'birthday'), 'Anniversaire de Léon', 'renommage : titre mis à jour');
select is((select count(*) from public.events where kind = 'birthday'), 1::bigint, 'renommage : pas de doublon');

-- ---------------------------------------------------------------- cagnotte : participant devenu parent
select pg_temp.login('c3000000-0000-0000-0000-000000000001');
select public.join_pot((select v from ids where k = 'velo'), 50, 'CHF');
select pg_temp.login('d3000000-0000-0000-0000-000000000001');
select public.join_pot((select v from ids where k = 'velo'), 30, 'CHF');
select is((select count(*) from public.contributions), 2::bigint, 'participant : voit les deux participations');
select public.join_household((select household_code from invite));
select is((select count(*) from public.contributions), 1::bigint, 'devenu parent : ne voit plus que la sienne');

-- ---------------------------------------------------------------- remerciements : URL de photo
select pg_temp.login('a3000000-0000-0000-0000-000000000001');
select throws_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'Merci', 'https://evil.example/x.jpg') $$,
  '23514', null, 'photo : URL hors Storage refusée');
select throws_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'Merci', 'http://127.0.0.1:55421/storage/v1/object/public/images/a/b.jpg') $$,
  '23514', null, 'photo : https obligatoire');
select lives_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'Merci',
  'https://abc.supabase.co/storage/v1/object/public/images/a3000000-0000-0000-0000-000000000001/11111111-1111-1111-1111-111111111111.jpg') $$,
  'photo : URL du bucket images acceptée');
select lives_ok($$ select public.send_thanks((select v from ids where k = 'velo'), 'Merci sans photo') $$, 'photo : facultative');

select pg_temp.login('c3000000-0000-0000-0000-000000000001');
select is((select count(*) from public.my_thanks()), 2::bigint, 'donatrice : reçoit les deux remerciements');

select * from finish();
rollback;

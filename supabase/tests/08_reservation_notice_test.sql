-- #58 Notification anonyme quand un cadeau est réservé.
--   alice : parente du foyer A (enfant Léo). bob, carol, dave : membres sans lien avec Léo.
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
  ('a8000000-0000-0000-0000-000000000001', 'alice8@test.local'),
  ('b8000000-0000-0000-0000-000000000001', 'bob8@test.local'),
  ('c8000000-0000-0000-0000-000000000001', 'carol8@test.local'),
  ('d8000000-0000-0000-0000-000000000001', 'dave8@test.local');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a8000000-0000-0000-0000-000000000001');
insert into ids values ('g', public.create_group('Famille Notif', 'Foyer d''Alice', 'CH'));
insert into ids select 'household_a', household_id from public.household_members where user_id = auth.uid();
with x as (insert into public.children (household_id, first_name) values ((select v from ids where k = 'household_a'), 'Léo') returning id)
  insert into ids select 'leo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Livre') returning id)
  insert into ids select 'livre', id from x;
with x as (insert into public.wish_items (child_id, title) values ((select v from ids where k = 'leo'), 'Ballon') returning id)
  insert into ids select 'ballon', id from x;
create temp table invite on commit drop as
  select invite_code from public.groups where id = (select v from ids where k = 'g');
grant all on invite to authenticated;

select pg_temp.login('b8000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));

-- ---------------------------------------------------------------- audience insuffisante
select public.reserve_item((select v from ids where k = 'velo'));
select pg_temp.logout();
select is((select count(*) from public.claim_reservation_notice((select v from ids where k = 'velo'),
                                                                'b8000000-0000-0000-0000-000000000001')), 0::bigint,
  'un seul destinataire possible : aucun envoi, sinon il devinerait l''acheteur');

-- ---------------------------------------------------------------- audience suffisante
select pg_temp.login('c8000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.login('d8000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select public.reserve_item((select v from ids where k = 'livre'));
select pg_temp.logout();

create temp table notice on commit drop as
  select * from public.claim_reservation_notice((select v from ids where k = 'livre'),
                                                'd8000000-0000-0000-0000-000000000001');

select is((select count(*) from notice), 2::bigint, 'deux destinataires : bob et carol');
select is((select count(*) from notice where user_id = 'd8000000-0000-0000-0000-000000000001'), 0::bigint,
  'jamais l''auteur de la réservation');
select is((select count(*) from notice where user_id = 'a8000000-0000-0000-0000-000000000001'), 0::bigint,
  'jamais la mère de Léo : le mode surprise tient');
select is((select distinct child_name from notice), 'Léo', 'le prénom de l''enfant est fourni');
select is((select distinct available_count from notice), 1::integer,
  'le nombre de cadeaux encore libres accompagne le message');

-- ---------------------------------------------------------------- agrégation sur 30 minutes
select pg_temp.login('c8000000-0000-0000-0000-000000000001');
select public.reserve_item((select v from ids where k = 'ballon'));
select pg_temp.logout();
select is((select count(*) from public.claim_reservation_notice((select v from ids where k = 'ballon'),
                                                                'c8000000-0000-0000-0000-000000000001')), 0::bigint,
  'seconde réservation dans la fenêtre : pas de notification en double');

-- Hors fenêtre, une nouvelle notification repart et agrège ce qui s'est passé.
update public.reservation_notifications_sent set sent_at = now() - interval '31 minutes';
update public.reservations set created_at = now() - interval '10 seconds'
where item_id = (select v from ids where k = 'ballon');
select is((select count(*) from public.claim_reservation_notice((select v from ids where k = 'ballon'),
                                                                'c8000000-0000-0000-0000-000000000001')), 2::bigint,
  'passé 30 minutes, une nouvelle notification part');

-- ---------------------------------------------------------------- anti-rejeu
update public.reservations set created_at = now() - interval '10 minutes'
where item_id = (select v from ids where k = 'livre');
update public.reservation_notifications_sent set sent_at = now() - interval '31 minutes';
select is((select count(*) from public.claim_reservation_notice((select v from ids where k = 'livre'),
                                                                'd8000000-0000-0000-0000-000000000001')), 0::bigint,
  'une réservation ancienne ne redéclenche pas de notification');

select is((select count(*) from public.claim_reservation_notice((select v from ids where k = 'livre'),
                                                                'b8000000-0000-0000-0000-000000000001')), 0::bigint,
  'on ne peut pas notifier pour la réservation de quelqu''un d''autre');

-- ---------------------------------------------------------------- droits
select pg_temp.login('b8000000-0000-0000-0000-000000000001');
select throws_ok($$
  select * from public.claim_reservation_notice((select v from ids where k = 'velo'), auth.uid())
$$, '42501', null, 'la fonction est réservée au service role');
select throws_ok($$ select count(*) from public.reservation_notifications_sent $$,
  '42501', null, 'la table de suivi n''est pas lisible par les utilisateurs');

select pg_temp.logout();
select is((select count(*) from public.reservation_notifications_sent), 2::bigint,
  'un envoi tracé par notification réellement partie, pas une par réservation');

select * from finish();
rollback;

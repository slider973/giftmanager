-- #59 Rappels d'achat côté serveur (J-30 / J-15 / J-7 / J-2).
--   alice (CH) : parente du foyer A (enfant Léo). bob (FR) et carol (FR) : membres.
begin;
select plan(14);

create function pg_temp.login(p_user uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
  select set_config('role', 'authenticated', true);
$$;
create function pg_temp.logout() returns void language sql as $$
  select set_config('role', 'postgres', true);
  select set_config('request.jwt.claims', '', true);
$$;

insert into auth.users (id, email) values
  ('a9000000-0000-0000-0000-000000000001', 'alice9@test.local'),
  ('b9000000-0000-0000-0000-000000000001', 'bob9@test.local'),
  ('c9000000-0000-0000-0000-000000000001', 'carol9@test.local');
update public.profiles set country = 'FR', currency = 'EUR'
where id in ('b9000000-0000-0000-0000-000000000001', 'c9000000-0000-0000-0000-000000000001');

create temp table ids (k text primary key, v uuid) on commit drop;
grant all on ids to authenticated;

select pg_temp.login('a9000000-0000-0000-0000-000000000001');
insert into ids values ('g', public.create_group('Famille Rappels', 'Foyer d''Alice', 'CH'));
insert into ids select 'household_a', household_id from public.household_members where user_id = auth.uid();
with x as (insert into public.children (household_id, first_name) values ((select v from ids where k = 'household_a'), 'Léo') returning id)
  insert into ids select 'leo', id from x;
-- Événement dans exactement 15 jours.
with x as (insert into public.events (group_id, kind, title, event_date)
           values ((select v from ids where k = 'g'), 'other', 'Fête', current_date + 15) returning id)
  insert into ids select 'fete', id from x;
with x as (insert into public.wish_items (child_id, event_id, title)
           values ((select v from ids where k = 'leo'), (select v from ids where k = 'fete'), 'Vélo') returning id)
  insert into ids select 'velo', id from x;
with x as (insert into public.wish_items (child_id, event_id, title)
           values ((select v from ids where k = 'leo'), (select v from ids where k = 'fete'), 'Livre') returning id)
  insert into ids select 'livre', id from x;
create temp table invite on commit drop as
  select invite_code from public.groups where id = (select v from ids where k = 'g');
grant all on invite to authenticated;

select pg_temp.login('b9000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select public.reserve_item((select v from ids where k = 'velo'));
select public.reserve_item((select v from ids where k = 'livre'));
select pg_temp.login('c9000000-0000-0000-0000-000000000001');
select public.join_group((select invite_code from invite));
select pg_temp.logout();

-- 18 h à Paris le jour J-15. Le cron tourne toutes les heures et ne sert que les bons fuseaux.
create temp table at18 on commit drop as
  select (current_date + time '18:00') at time zone 'Europe/Paris' as t;
grant all on at18 to authenticated;

-- ---------------------------------------------------------------- candidats
select is((select count(*) from private.purchase_reminder_candidates((select t from at18))), 1::bigint,
  'un seul rappel : bob, pour ses deux réservations');
select is((select item_count from private.purchase_reminder_candidates((select t from at18))), 2,
  'les deux cadeaux sont comptés dans un seul message');
select is((select days_before from private.purchase_reminder_candidates((select t from at18))), 15,
  'échéance J-15 reconnue');
select is((select user_id from private.purchase_reminder_candidates((select t from at18))),
  'b9000000-0000-0000-0000-000000000001', 'seul l''auteur des réservations est destinataire');
select is((select count(*) from private.purchase_reminder_candidates((select t from at18))
           where user_id = 'a9000000-0000-0000-0000-000000000001'), 0::bigint,
  'la mère de Léo ne reçoit rien : elle n''a rien réservé');
select is((select count(*) from private.purchase_reminder_candidates((select t from at18))
           where user_id = 'c9000000-0000-0000-0000-000000000001'), 0::bigint,
  'carol non plus : aucune réservation en attente');

-- ---------------------------------------------------------------- heure et fuseau
select is((select count(*) from private.purchase_reminder_candidates(
             (current_date + time '09:00') at time zone 'Europe/Paris')), 0::bigint,
  'hors de 18 h locales : rien ne part');
select is((select count(*) from private.purchase_reminder_candidates(
             (current_date + time '18:00') at time zone 'America/New_York')), 0::bigint,
  'il est 18 h à New York mais minuit à Paris : bob n''est pas servi');

-- ---------------------------------------------------------------- échéances
select is((select count(*) from private.purchase_reminder_candidates(
             (current_date + time '18:00' - interval '1 day') at time zone 'Europe/Paris')), 0::bigint,
  'J-16 n''est pas une échéance de rappel');

-- ---------------------------------------------------------------- idempotence
select is((select count(*) from public.claim_purchase_reminders((select t from at18))), 1::bigint,
  'claim : un rappel à envoyer');
select is((select count(*) from public.claim_purchase_reminders((select t from at18))), 0::bigint,
  'claim idempotent : pas de rappel en double le même jour');

-- ---------------------------------------------------------------- cadeau acheté
delete from public.purchase_reminders_sent;
update public.reservations set status = 'purchased'
where item_id in ((select v from ids where k = 'velo'), (select v from ids where k = 'livre'));
select is((select count(*) from private.purchase_reminder_candidates((select t from at18))), 0::bigint,
  'tout acheté : plus aucun rappel');

-- ---------------------------------------------------------------- préférence
update public.reservations set status = 'reserved'
where item_id in ((select v from ids where k = 'velo'), (select v from ids where k = 'livre'));
update public.profiles set notify_purchase_reminders = false where id = 'b9000000-0000-0000-0000-000000000001';
select is((select count(*) from private.purchase_reminder_candidates((select t from at18))), 0::bigint,
  'préférence désactivée : bob n''est plus notifié');

-- ---------------------------------------------------------------- droits
select pg_temp.login('b9000000-0000-0000-0000-000000000001');
select throws_ok($$ select * from public.claim_purchase_reminders() $$,
  '42501', null, 'claim réservé au service role');

select * from finish();
rollback;

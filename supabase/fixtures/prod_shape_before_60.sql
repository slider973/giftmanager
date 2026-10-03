-- Reproduit la structure réelle de la base de production avant #60, pour éprouver la
-- migration de fusion sur un cas que le seed de démonstration ne couvrait pas :
-- un même parent avec un foyer par groupe, le même enfant saisi deux fois, et des
-- cadeaux rattachés aux événements de chaque groupe.
--
-- Usage : psql "$LOCAL_DB" -f supabase/fixtures/prod_shape_before_60.sql
--   puis supabase db push (ou l'application manuelle de 20261003000200) pour vérifier.

insert into auth.users (id, email) values
  ('11111111-1111-1111-1111-111111111111', 'jonathan@prod.local'),
  ('22222222-2222-2222-2222-222222222222', 'kenny@prod.local'),
  ('33333333-3333-3333-3333-333333333333', 'arthur@prod.local');

update public.profiles set display_name = 'jonathan', country = 'CH', currency = 'CHF', onboarded = true
where id = '11111111-1111-1111-1111-111111111111';
update public.profiles set display_name = 'Kenny', country = 'FR', currency = 'EUR', onboarded = true
where id = '22222222-2222-2222-2222-222222222222';
update public.profiles set display_name = 'Arthur', country = 'FR', currency = 'EUR', onboarded = true
where id = '33333333-3333-3333-3333-333333333333';

-- Deux groupes, comme Lazzarotto et Celiba.
insert into public.groups (id, name, invite_code, created_by) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'Lazzarotto', 'LAZZ26', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-0000-0000-0000-000000000002', 'Celiba', 'CELI26', '11111111-1111-1111-1111-111111111111');
insert into public.group_members (group_id, user_id) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111'),
  ('aaaaaaaa-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222');

-- jonathan a UN FOYER PAR GROUPE : c'est ce que #60 doit fusionner.
insert into public.households (id, group_id, name, country) values
  ('bbbbbbbb-0000-0000-0000-00000000000a', 'aaaaaaaa-0000-0000-0000-000000000001', 'Foyer de jonathan', 'CH'),
  ('bbbbbbbb-0000-0000-0000-00000000000b', 'aaaaaaaa-0000-0000-0000-000000000002', 'Foyer de jonathan', 'CH'),
  ('bbbbbbbb-0000-0000-0000-00000000000c', 'aaaaaaaa-0000-0000-0000-000000000002', 'Foyer de Kenny', 'FR'),
  ('bbbbbbbb-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000001', 'Foyer de Arthur', 'FR');
insert into public.household_members (household_id, group_id, user_id) values
  ('bbbbbbbb-0000-0000-0000-00000000000a', 'aaaaaaaa-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111'),
  ('bbbbbbbb-0000-0000-0000-00000000000b', 'aaaaaaaa-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111'),
  ('bbbbbbbb-0000-0000-0000-00000000000c', 'aaaaaaaa-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222'),
  ('bbbbbbbb-0000-0000-0000-00000000000d', 'aaaaaaaa-0000-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333');

-- Eyden saisi deux fois, un exemplaire par groupe : les deux doivent n'en former qu'un.
insert into public.children (id, household_id, first_name, birthdate) values
  ('cccccccc-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-00000000000a', 'Eyden', '2017-05-27'),
  ('cccccccc-0000-0000-0000-000000000002', 'bbbbbbbb-0000-0000-0000-00000000000b', 'Eyden', '2017-05-27'),
  ('cccccccc-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-00000000000c', 'ALinnah', '2016-10-03'),
  ('cccccccc-0000-0000-0000-000000000004', 'bbbbbbbb-0000-0000-0000-00000000000d', 'Epril', '2019-03-27');

-- Un Noël par groupe (ils doivent fusionner) et des anniversaires créés par le trigger.
insert into public.events (id, group_id, kind, title, event_date, child_id) values
  ('dddddddd-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'christmas', 'Noël 2026', '2026-12-25', null),
  ('dddddddd-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000002', 'christmas', 'Noël 2026', '2026-12-25', null);

-- Cadeaux rattachés aux événements de chaque groupe : le cas qui faisait échouer la fusion.
insert into public.wish_items (id, child_id, event_id, title, created_by) values
  ('eeeeeeee-0000-0000-0000-000000000001', 'cccccccc-0000-0000-0000-000000000001',
   'dddddddd-0000-0000-0000-000000000001', 'Dell Tower Plus', '11111111-1111-1111-1111-111111111111'),
  ('eeeeeeee-0000-0000-0000-000000000002', 'cccccccc-0000-0000-0000-000000000002',
   'dddddddd-0000-0000-0000-000000000002', 'Synology DS214Play', '11111111-1111-1111-1111-111111111111'),
  ('eeeeeeee-0000-0000-0000-000000000003', 'cccccccc-0000-0000-0000-000000000003',
   'dddddddd-0000-0000-0000-000000000002', 'Remington Lisseur', '22222222-2222-2222-2222-222222222222');

-- Le même talkie-walkie saisi deux fois pour Epril, sur deux événements différents (#61).
insert into public.wish_items (id, child_id, event_id, title, created_by)
select 'eeeeeeee-0000-0000-0000-000000000004', 'cccccccc-0000-0000-0000-000000000004',
       (select id from public.events where kind = 'birthday' and child_id = 'cccccccc-0000-0000-0000-000000000004'),
       'TIATUA Talkie Walkie', '33333333-3333-3333-3333-333333333333';
insert into public.wish_items (id, child_id, event_id, title, created_by) values
  ('eeeeeeee-0000-0000-0000-000000000005', 'cccccccc-0000-0000-0000-000000000004',
   'dddddddd-0000-0000-0000-000000000001', 'TIATUA Talkie Walkie', '33333333-3333-3333-3333-333333333333');

-- Une idée et des réservations, pour vérifier qu'elles survivent à la fusion.
insert into public.wish_items (id, child_id, event_id, kind, title, created_by) values
  ('eeeeeeee-0000-0000-0000-000000000006', 'cccccccc-0000-0000-0000-000000000003',
   'dddddddd-0000-0000-0000-000000000002', 'idea', 'Nintendo Switch 2', '11111111-1111-1111-1111-111111111111');

insert into public.reservations (item_id, user_id, status) values
  ('eeeeeeee-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'purchased'),
  ('eeeeeeee-0000-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'purchased'),
  ('eeeeeeee-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'reserved');

-- Données de démo pour le développement local uniquement (supabase db reset).
-- Comptes email / mot de passe "demo1234" : alice@demo.local (CH), bob@demo.local (FR), carol@demo.local (grand-mère).

create function pg_temp.demo_user(p_id uuid, p_email text, p_name text, p_country text, p_currency text)
returns void language plpgsql as $$
begin
  insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
                          raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
                          confirmation_token, recovery_token, email_change_token_new, email_change)
  values ('00000000-0000-0000-0000-000000000000', p_id, 'authenticated', 'authenticated', p_email,
          extensions.crypt('demo1234', extensions.gen_salt('bf')), now(),
          '{"provider":"email","providers":["email"]}', json_build_object('full_name', p_name), now(), now(),
          '', '', '', '');
  insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
  values (gen_random_uuid(), p_id, p_id::text, 'email',
          json_build_object('sub', p_id::text, 'email', p_email, 'email_verified', true), now(), now(), now());
  update public.profiles set display_name = p_name, country = p_country, currency = p_currency, onboarded = true
  where id = p_id;
end;
$$;

select pg_temp.demo_user('a1111111-1111-1111-1111-111111111111', 'alice@demo.local', 'Alice', 'CH', 'CHF');
select pg_temp.demo_user('b2222222-2222-2222-2222-222222222222', 'bob@demo.local', 'Bob', 'FR', 'EUR');
select pg_temp.demo_user('c3333333-3333-3333-3333-333333333333', 'carol@demo.local', 'Mamie Carol', 'FR', 'EUR');

insert into public.groups (id, name, invite_code, created_by)
values ('99999999-0000-0000-0000-000000000001', 'Famille Démo', 'DEMO26', 'a1111111-1111-1111-1111-111111111111');
insert into public.group_members (group_id, user_id) values
  ('99999999-0000-0000-0000-000000000001', 'a1111111-1111-1111-1111-111111111111'),
  ('99999999-0000-0000-0000-000000000001', 'b2222222-2222-2222-2222-222222222222'),
  ('99999999-0000-0000-0000-000000000001', 'c3333333-3333-3333-3333-333333333333');

insert into public.households (id, group_id, name, country) values
  ('88888888-0000-0000-0000-00000000000a', '99999999-0000-0000-0000-000000000001', 'Foyer d''Alice', 'CH'),
  ('88888888-0000-0000-0000-00000000000b', '99999999-0000-0000-0000-000000000001', 'Foyer de Bob', 'FR');
insert into public.household_members (household_id, group_id, user_id) values
  ('88888888-0000-0000-0000-00000000000a', '99999999-0000-0000-0000-000000000001', 'a1111111-1111-1111-1111-111111111111'),
  ('88888888-0000-0000-0000-00000000000b', '99999999-0000-0000-0000-000000000001', 'b2222222-2222-2222-2222-222222222222');

insert into public.children (id, household_id, first_name, birthdate, avatar_emoji, avatar_color) values
  ('77777777-0000-0000-0000-000000000001', '88888888-0000-0000-0000-00000000000a', 'Léo', '2018-03-12', '🦖', 'pastelMint'),
  ('77777777-0000-0000-0000-000000000002', '88888888-0000-0000-0000-00000000000b', 'Emma', '2021-06-03', '🦄', 'pastelLavender'),
  ('77777777-0000-0000-0000-000000000003', '88888888-0000-0000-0000-00000000000b', 'Hugo', '2016-11-20', '⚽️', 'pastelBlue');

insert into public.events (id, group_id, kind, title, event_date, child_id, created_by) values
  ('66666666-0000-0000-0000-000000000001', '99999999-0000-0000-0000-000000000001', 'christmas', 'Noël 2026', '2026-12-25', null, 'a1111111-1111-1111-1111-111111111111'),
  ('66666666-0000-0000-0000-000000000002', '99999999-0000-0000-0000-000000000001', 'birthday', 'Anniversaire de Léo', '2027-03-12', '77777777-0000-0000-0000-000000000001', 'a1111111-1111-1111-1111-111111111111');

insert into public.wish_items (id, child_id, event_id, title, notes, priority, position, owned, created_by) values
  ('55555555-0000-0000-0000-000000000001', '77777777-0000-0000-0000-000000000001', '66666666-0000-0000-0000-000000000001', 'LEGO Technic McLaren F1 42141', 'La voiture orange !', 1, 0, false, 'a1111111-1111-1111-1111-111111111111'),
  ('55555555-0000-0000-0000-000000000002', '77777777-0000-0000-0000-000000000001', '66666666-0000-0000-0000-000000000001', 'Casque Sony WH-1000XM5', null, 0, 1, false, 'a1111111-1111-1111-1111-111111111111'),
  ('55555555-0000-0000-0000-000000000003', '77777777-0000-0000-0000-000000000001', '66666666-0000-0000-0000-000000000001', 'Nintendo Switch OLED', null, 1, 2, false, 'a1111111-1111-1111-1111-111111111111'),
  ('55555555-0000-0000-0000-000000000004', '77777777-0000-0000-0000-000000000001', null, 'Trottinette', null, 0, 0, true, 'a1111111-1111-1111-1111-111111111111'),
  ('55555555-0000-0000-0000-000000000005', '77777777-0000-0000-0000-000000000002', '66666666-0000-0000-0000-000000000001', 'Maison de poupée', null, 1, 0, false, 'b2222222-2222-2222-2222-222222222222'),
  ('55555555-0000-0000-0000-000000000006', '77777777-0000-0000-0000-000000000003', '66666666-0000-0000-0000-000000000001', 'Maillot PSG 2026', 'Taille 10 ans', 0, 0, false, 'b2222222-2222-2222-2222-222222222222');

insert into public.item_links (item_id, url, store, country, price, currency) values
  ('55555555-0000-0000-0000-000000000001', 'https://www.galaxus.ch/fr/s1/product/lego-technic-mclaren-formula-1-42141', 'Galaxus', 'CH', 199.00, 'CHF'),
  ('55555555-0000-0000-0000-000000000001', 'https://www.amazon.fr/dp/B09QFZ2X7C', 'Amazon', 'FR', 199.99, 'EUR'),
  ('55555555-0000-0000-0000-000000000002', 'https://www.amazon.fr/dp/B09Y2MYL5C', 'Amazon', 'FR', 299.00, 'EUR'),
  ('55555555-0000-0000-0000-000000000003', 'https://www.fnac.com/Console-Nintendo-Switch-OLED', 'Fnac', 'FR', 349.00, 'EUR'),
  ('55555555-0000-0000-0000-000000000005', 'https://www.manor.ch/fr/maison-de-poupee', 'Manor', 'CH', 129.00, 'CHF'),
  ('55555555-0000-0000-0000-000000000006', 'https://www.nike.com/fr/maillot-psg-2026', 'Nike', 'FR', 89.00, 'EUR');

-- Carol (grand-mère) a déjà réservé le casque de Léo.
insert into public.reservations (item_id, user_id, status) values
  ('55555555-0000-0000-0000-000000000002', 'c3333333-3333-3333-3333-333333333333', 'reserved');

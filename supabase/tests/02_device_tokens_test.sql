-- Jetons d'appareils (#15) : chacun ne voit et n'enregistre que les siens.
begin;
select plan(13);

insert into auth.users (id, email) values
  ('e0000000-0000-0000-0000-000000000001', 'eve@test.local'),
  ('f0000000-0000-0000-0000-000000000001', 'frank@test.local');

select set_config('request.jwt.claims', '{"sub":"e0000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select set_config('role', 'authenticated', true);
select lives_ok($$ select public.register_device(repeat('a', 64), 'sandbox') $$, 'enregistrer son appareil');
select is((select count(*) from public.device_tokens), 1::bigint, 'on voit son propre jeton');

select set_config('request.jwt.claims', '{"sub":"f0000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is((select count(*) from public.device_tokens), 0::bigint, 'on ne voit pas les jetons des autres');
select throws_ok($$ insert into public.device_tokens (token, user_id) values (repeat('b', 64), 'e0000000-0000-0000-0000-000000000001') $$,
  '42501', null, 'pas d''insertion directe (ni pour autrui)');

-- Plateforme (#76)
select set_config('request.jwt.claims', '{"sub":"e0000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is((select platform from public.device_tokens where token = repeat('a', 64)), 'ios', 'appel à 2 paramètres : plateforme ios par défaut');
select set_config('request.jwt.claims', '{"sub":"f0000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select lives_ok($$ select public.register_device(repeat('c', 180), 'sandbox', 'android') $$, 'jeton Android de 180 caractères accepté');
select is((select platform || '/' || environment from public.device_tokens where token = repeat('c', 180)), 'android/production',
  'android : plateforme enregistrée, environnement forcé à production');
select throws_ok($$ select public.register_device(repeat('d', 4097), 'production', 'android') $$,
  '23514', null, 'jeton de plus de 4096 caractères refusé');
select throws_ok($$ select public.register_device(repeat('e', 31)) $$, '23514', null, 'jeton trop court refusé');
select throws_ok($$ select public.register_device(repeat('f', 64), 'production', 'windows') $$,
  'P0001', 'INVALID_PLATFORM', 'plateforme invalide refusée');
-- Réattribution : le même jeton passe à un autre utilisateur et à une autre plateforme.
select set_config('request.jwt.claims', '{"sub":"e0000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select lives_ok($$ select public.register_device(repeat('c', 180), 'production', 'ios') $$, 'réattribution d''un jeton existant');
select is((select platform from public.device_tokens where token = repeat('c', 180)), 'ios', 'la réattribution met à jour la plateforme');

select set_config('role', 'anon', true);
select throws_ok($$ select public.register_device(repeat('g', 64)) $$, '42501', null, 'anon ne peut pas enregistrer d''appareil');

select * from finish();
rollback;

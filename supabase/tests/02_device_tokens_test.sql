-- Jetons d'appareils (#15) : chacun ne voit et n'enregistre que les siens.
begin;
select plan(4);

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

select * from finish();
rollback;

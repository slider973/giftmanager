-- Notifications push Android (#76) : les jetons FCM s'ajoutent aux jetons APNs.
-- `platform` aiguille l'envoi (ios -> APNs, android -> FCM). Les jetons FCM sont bien plus longs
-- que ceux d'APNs (environ 160 caractères, parfois davantage) : la borne passe à 4096.

alter table public.device_tokens
  add column platform text not null default 'ios' check (platform in ('ios', 'android'));

alter table public.device_tokens drop constraint device_tokens_token_check;
alter table public.device_tokens add constraint device_tokens_token_check
  check (char_length(token) between 32 and 4096);

-- Nouvelle signature : l'ancienne est retirée pour qu'un appel à 2 paramètres (clients iOS déjà
-- publiés) résolve sans ambiguïté vers la nouvelle, p_platform prenant sa valeur par défaut 'ios'.
drop function public.register_device(text, text);

-- Enregistre (ou réattribue) le jeton de l'appareil courant.
create function public.register_device(
  p_token text, p_environment text default 'production', p_platform text default 'ios'
) returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_platform is null or p_platform not in ('ios', 'android') then raise exception 'INVALID_PLATFORM'; end if;
  insert into public.device_tokens (token, user_id, environment, platform, updated_at)
  values (
    p_token, auth.uid(),
    -- FCM n'a pas d'environnement sandbox.
    case when p_platform = 'android' then 'production' else p_environment end,
    p_platform, now()
  )
  on conflict (token) do update set
    user_id = excluded.user_id, environment = excluded.environment,
    platform = excluded.platform, updated_at = now();
end;
$$;

revoke execute on function public.register_device(text, text, text) from public, anon;
grant execute on function public.register_device(text, text, text) to authenticated;

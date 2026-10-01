-- Notifications push (#15) : jetons APNs des appareils.
-- Lus uniquement par la fonction Edge `notify-new-items` (service role). Aucune notification
-- ne concerne une réservation : elles ne portent que sur l'ajout de souhaits ou d'idées.

create table public.device_tokens (
  token text primary key check (char_length(token) between 32 and 200),
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  environment text not null default 'production' check (environment in ('sandbox', 'production')),
  updated_at timestamptz not null default now()
);
create index on public.device_tokens (user_id);

alter table public.device_tokens enable row level security;
revoke all on public.device_tokens from anon;

create policy "jetons : lecture des siens" on public.device_tokens for select to authenticated
  using (user_id = auth.uid());
create policy "jetons : suppression des siens" on public.device_tokens for delete to authenticated
  using (user_id = auth.uid());

-- Enregistre (ou réattribue) le jeton de l'appareil courant.
create function public.register_device(p_token text, p_environment text default 'production') returns void
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  insert into public.device_tokens (token, user_id, environment, updated_at)
  values (p_token, auth.uid(), p_environment, now())
  on conflict (token) do update set user_id = excluded.user_id, environment = excluded.environment, updated_at = now();
end;
$$;

revoke execute on function public.register_device(text, text) from public, anon;
grant execute on function public.register_device(text, text) to authenticated;

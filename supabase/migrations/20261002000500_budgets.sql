-- Budget par enfant et/ou par événement (#40).
-- Strictement privé : seul son auteur lit et modifie ses budgets (aucune policy pour les autres,
-- parents de l'enfant compris).

create table public.budgets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade default auth.uid(),
  child_id uuid references public.children (id) on delete cascade,
  event_id uuid references public.events (id) on delete cascade,
  amount numeric(10, 2) not null check (amount > 0),
  currency text not null check (currency ~ '^[A-Z]{3}$'),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (child_id is not null or event_id is not null),
  -- Un budget par périmètre (enfant, événement) et par devise.
  unique nulls not distinct (user_id, child_id, event_id, currency)
);
create index on public.budgets (child_id);
create index on public.budgets (event_id);

create trigger budgets_touch before update on public.budgets
  for each row execute function public.touch_updated_at();

alter table public.budgets enable row level security;
revoke all on public.budgets from anon;

create policy "budgets : lecture des siens" on public.budgets for select to authenticated
  using (user_id = auth.uid());
create policy "budgets : création dans son groupe" on public.budgets for insert to authenticated
  with check (
    user_id = auth.uid()
    and (child_id is null or private.can_view_child(child_id))
    and (event_id is null or private.is_group_member((select e.group_id from public.events e where e.id = event_id)))
  );
create policy "budgets : modification des siens" on public.budgets for update to authenticated
  using (user_id = auth.uid())
  with check (
    user_id = auth.uid()
    and (child_id is null or private.can_view_child(child_id))
    and (event_id is null or private.is_group_member((select e.group_id from public.events e where e.id = event_id)))
  );
create policy "budgets : suppression des siens" on public.budgets for delete to authenticated
  using (user_id = auth.uid());

-- Crée ou met à jour le budget d'un périmètre (enfant et/ou événement) pour une devise. Retourne son id.
create function public.set_budget(p_child uuid, p_event uuid, p_amount numeric, p_currency text)
returns uuid
language plpgsql volatile set search_path = '' as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_child is null and p_event is null then raise exception 'BUDGET_SCOPE_REQUIRED'; end if;
  -- Exécutée avec les droits de l'appelant : la RLS vérifie l'accès à l'enfant et à l'événement.
  select id into v_id from public.budgets
  where user_id = auth.uid() and child_id is not distinct from p_child
    and event_id is not distinct from p_event and currency = upper(p_currency);
  if v_id is null then
    insert into public.budgets (child_id, event_id, amount, currency)
    values (p_child, p_event, p_amount, upper(p_currency)) returning id into v_id;
  else
    update public.budgets set amount = p_amount where id = v_id;
  end if;
  return v_id;
end;
$$;

-- Budgets de l'utilisateur avec le montant déjà dépensé dans la devise du budget :
-- prix (le plus bas des liens dans cette devise) de ses cadeaux réservés ou achetés + ses parts de cagnotte.
-- Un budget d'enfant couvre tous les événements ; un budget d'événement, les cadeaux rattachés à cet événement.
create function public.my_budgets()
returns table (
  id uuid, child_id uuid, child_name text, event_id uuid, event_title text,
  amount numeric, currency text, spent numeric
)
language sql stable security definer set search_path = '' as $$
  select b.id, b.child_id, c.first_name, b.event_id, e.title, b.amount, b.currency,
         coalesce((
           select sum(x.v) from (
             select (select min(l.price) from public.item_links l
                     where l.item_id = i.id and l.currency = b.currency and l.price is not null) as v
             from public.reservations r
             join public.wish_items i on i.id = r.item_id
             where r.user_id = b.user_id
               and (b.child_id is null or i.child_id = b.child_id)
               and (b.event_id is null or i.event_id = b.event_id)
             union all
             select k.amount
             from public.contributions k
             join public.wish_items i on i.id = k.item_id
             where k.user_id = b.user_id and k.currency = b.currency
               and (b.child_id is null or i.child_id = b.child_id)
               and (b.event_id is null or i.event_id = b.event_id)
           ) x
         ), 0) as spent
  from public.budgets b
  left join public.children c on c.id = b.child_id
  left join public.events e on e.id = b.event_id
  where b.user_id = auth.uid()
  order by e.event_date nulls last, c.first_name nulls last, b.currency;
$$;

revoke execute on function public.set_budget(uuid, uuid, numeric, text), public.my_budgets() from public, anon;
grant execute on function public.set_budget(uuid, uuid, numeric, text), public.my_budgets() to authenticated;

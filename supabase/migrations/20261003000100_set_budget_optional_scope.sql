-- set_budget : enfant et événement optionnels (#53).
-- L'app appelle la fonction par paramètres nommés et omet ceux qui sont nuls (budget d'événement seul,
-- ou d'enfant seul) ; sans valeurs par défaut, PostgREST ne trouvait pas la fonction.
-- Même signature de types : droits et appels positionnels inchangés.
create or replace function public.set_budget(
  p_child uuid default null, p_event uuid default null, p_amount numeric default null, p_currency text default null
)
returns uuid
language plpgsql volatile set search_path = '' as $$
declare
  v_id uuid;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_child is null and p_event is null then raise exception 'BUDGET_SCOPE_REQUIRED'; end if;
  if p_amount is null or p_currency is null then raise exception 'BUDGET_AMOUNT_REQUIRED'; end if;
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

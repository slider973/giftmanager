-- Suivi des prix et du stock des cadeaux réservés (#39).
-- Une fonction Edge quotidienne relit les liens des cadeaux réservés non achetés et enregistre ici
-- ce qu'elle trouve. L'historique n'est lisible que par l'auteur de la réservation : ni les parents
-- de l'enfant, ni les autres membres. Le prix de `item_links` (visible des parents) n'est jamais modifié.

create table public.price_history (
  id uuid primary key default gen_random_uuid(),
  link_id uuid not null references public.item_links (id) on delete cascade,
  price numeric(10, 2) check (price >= 0),
  currency text check (currency ~ '^[A-Z]{3}$'),
  in_stock boolean,
  checked_at timestamptz not null default now()
);
create index on public.price_history (link_id, checked_at desc);

-- Dernière alerte envoyée par lien : une même baisse ou rupture n'est notifiée qu'une fois.
create table public.price_alerts_sent (
  link_id uuid not null references public.item_links (id) on delete cascade,
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null check (kind in ('price_drop', 'out_of_stock')),
  price numeric(10, 2),
  sent_at timestamptz not null default now(),
  primary key (link_id, user_id, kind)
);

alter table public.price_history enable row level security;
alter table public.price_alerts_sent enable row level security;
revoke all on public.price_history, public.price_alerts_sent from anon;
revoke all on public.price_alerts_sent from authenticated;

create function private.is_item_reserver(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.reservations where item_id = p_item and user_id = auth.uid());
$$;

create policy "historique de prix : lecture par l'auteur de la réservation" on public.price_history
  for select to authenticated
  using (private.is_item_reserver((select l.item_id from public.item_links l where l.id = link_id)));

-- Liens à relire : cadeaux réservés, non achetés, pas déjà possédés. Les moins récemment vérifiés d'abord.
-- Réservée au service role ; ne renvoie jamais l'identité du réservant.
create function public.links_to_check(p_limit integer default 200)
returns table (link_id uuid, item_id uuid, url text)
language sql stable security definer set search_path = '' as $$
  select l.id, l.item_id, l.url
  from public.item_links l
  join public.reservations r on r.item_id = l.item_id and r.status = 'reserved'
  join public.wish_items i on i.id = l.item_id and not i.owned
  order by (select max(h.checked_at) from public.price_history h where h.link_id = l.id) nulls first
  limit greatest(p_limit, 0);
$$;

-- Enregistre une relecture et renvoie la notification à envoyer, le cas échéant (au plus une ligne).
--   - « price_drop »   : prix inférieur au dernier prix connu (historique, sinon prix saisi du lien),
--                        dans la même devise, et inférieur à la dernière baisse déjà notifiée ;
--   - « out_of_stock » : passage de « en stock » (ou inconnu) à « plus en stock » ;
--   - destinataire : uniquement l'auteur de la réservation (jamais les parents).
-- Une relecture sans information (prix et stock nuls) n'est pas enregistrée.
-- Une remontée du prix ou le retour en stock réarme les alertes.
create function public.record_price_check(p_link uuid, p_price numeric, p_currency text, p_in_stock boolean)
returns table (kind text, user_id uuid, item_id uuid, title text, old_price numeric, new_price numeric, currency text)
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_link public.item_links;
  v_holder uuid;
  v_title text;
  v_prev_price numeric;
  v_prev_currency text;
  v_prev_stock boolean;
  v_cur text := upper(p_currency);
begin
  select * into v_link from public.item_links where id = p_link;
  if v_link.id is null or (p_price is null and p_in_stock is null) then return; end if;

  select h.price, h.currency into v_prev_price, v_prev_currency
  from public.price_history h where h.link_id = p_link and h.price is not null
  order by h.checked_at desc, h.id desc limit 1;
  if not found then v_prev_price := v_link.price; v_prev_currency := v_link.currency; end if;
  select h.in_stock into v_prev_stock
  from public.price_history h where h.link_id = p_link and h.in_stock is not null
  order by h.checked_at desc, h.id desc limit 1;

  insert into public.price_history (link_id, price, currency, in_stock)
  values (p_link, p_price, v_cur, p_in_stock);

  select r.user_id into v_holder from public.reservations r where r.item_id = v_link.item_id and r.status = 'reserved';
  if v_holder is null then return; end if;
  select i.title into v_title from public.wish_items i where i.id = v_link.item_id and not i.owned;
  if v_title is null then return; end if;

  if p_price is not null and v_prev_price is not null and p_price >= v_prev_price then
    delete from public.price_alerts_sent a where a.link_id = p_link and a.kind = 'price_drop';
  end if;
  if p_in_stock is true then
    delete from public.price_alerts_sent a where a.link_id = p_link and a.kind = 'out_of_stock';
  end if;

  if p_price is not null and v_prev_price is not null
     and (v_cur is null or v_prev_currency is null or v_cur = v_prev_currency)
     and p_price < v_prev_price then
    insert into public.price_alerts_sent as a (link_id, user_id, kind, price)
    values (p_link, v_holder, 'price_drop', p_price)
    on conflict on constraint price_alerts_sent_pkey do update set price = excluded.price, sent_at = now()
      where a.price is null or excluded.price < a.price;
    if found then
      return query select 'price_drop'::text, v_holder, v_link.item_id, v_title, v_prev_price, p_price,
                          coalesce(v_cur, v_prev_currency);
    end if;
  elsif p_in_stock is false and v_prev_stock is not false then
    insert into public.price_alerts_sent (link_id, user_id, kind) values (p_link, v_holder, 'out_of_stock')
    on conflict on constraint price_alerts_sent_pkey do nothing;
    if found then
      return query select 'out_of_stock'::text, v_holder, v_link.item_id, v_title, null::numeric, null::numeric, null::text;
    end if;
  end if;
end;
$$;

-- Historique des liens d'un cadeau : uniquement si l'utilisateur courant l'a réservé (RLS), sinon aucune ligne.
create function public.my_price_history(p_item uuid)
returns table (link_id uuid, url text, price numeric, currency text, in_stock boolean, checked_at timestamptz)
language sql stable set search_path = '' as $$
  select h.link_id, l.url, h.price, h.currency, h.in_stock, h.checked_at
  from public.price_history h
  join public.item_links l on l.id = h.link_id
  where l.item_id = p_item
  order by h.checked_at desc;
$$;

-- Tous les jours à 05:00 UTC.
select cron.schedule('track-prices', '0 5 * * *', $$select private.invoke_edge_function('track-prices')$$);

revoke execute on function private.is_item_reserver(uuid) from public, anon;
grant execute on function private.is_item_reserver(uuid) to authenticated;
revoke execute on function
  public.links_to_check(integer), public.record_price_check(uuid, numeric, text, boolean)
from public, anon, authenticated;
grant execute on function public.links_to_check(integer), public.record_price_check(uuid, numeric, text, boolean) to service_role;
revoke execute on function public.my_price_history(uuid) from public, anon;
grant execute on function public.my_price_history(uuid) to authenticated;

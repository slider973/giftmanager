-- #57 — Les idées sont soumises aux parents au lieu de leur être cachées.
--
-- Avant : une idée (kind = 'idea') était invisible aux parents de l'enfant. Dans un groupe
-- à deux foyers, toute idée visait forcément l'enfant de l'autre : elle n'avait donc aucun
-- destinataire et disparaissait sans un mot. Surtout, la seule personne qui sait si le cadeau
-- convient (âge, doublon, règles de la maison) était justement écartée.
--
-- Après : une idée est par défaut « en attente » (review_status = 'pending') et visible de
-- ses parents, qui l'acceptent, la refusent ou signalent que l'enfant l'a déjà. Le parent
-- n'apprend jamais qui offrira : le secret porte sur la réservation, pas sur l'objet.
-- L'ancien comportement reste possible ('none') quand l'auteur veut la surprise totale.

alter table public.wish_items
  add column review_status text not null default 'none'
    check (review_status in ('none', 'pending', 'accepted', 'rejected', 'owned_already')),
  add column reviewed_by uuid references auth.users (id) on delete set null,
  add column reviewed_at timestamptz,
  add column review_note text check (char_length(review_note) <= 500);

-- Un souhait n'est jamais soumis à validation ; seule une idée peut l'être. Les deux statuts
-- finaux qui font entrer l'objet dans la liste ('accepted', 'owned_already') portent kind = 'wish'.
alter table public.wish_items
  add constraint wish_items_review_scope check (
    review_status = 'none'
    or (kind = 'idea' and review_status in ('pending', 'rejected'))
    or (kind = 'wish' and review_status in ('accepted', 'owned_already'))
  );

create index on public.wish_items (child_id, review_status) where kind = 'idea';

-- ---------------------------------------------------------------------------
-- Visibilité : une idée soumise devient visible de ses parents.
-- ---------------------------------------------------------------------------

create or replace function private.can_view_item(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.wish_items i
    where i.id = p_item
      and private.can_view_child(i.child_id)
      and (
        i.kind = 'wish'
        or not private.is_parent_of(i.child_id)
        -- L'idée soumise est faite pour être lue par les parents.
        or i.review_status <> 'none'
      )
  );
$$;

drop policy "cadeaux visibles (idées masquées aux parents)" on public.wish_items;
create policy "cadeaux visibles (idées soumises visibles des parents)" on public.wish_items
  for select to authenticated
  using (
    private.can_view_child(child_id)
    and (
      kind = 'wish'
      or not private.is_parent_of(child_id)
      or review_status <> 'none'
    )
    -- Une idée refusée ne reste visible que de son auteur et des parents.
    and (
      review_status not in ('rejected', 'owned_already')
      or created_by = auth.uid()
      or private.is_parent_of(child_id)
    )
  );

-- L'auteur modifie son idée tant qu'elle n'est pas tranchée ; le verdict passe par review_idea.
create or replace function private.can_edit_item(p_item uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.wish_items i
    where i.id = p_item
      and (
        (i.kind = 'wish' and private.is_parent_of(i.child_id))
        or (i.kind = 'idea' and i.created_by = auth.uid() and not private.is_parent_of(i.child_id))
      )
  );
$$;

-- Une idée naît « en attente » ou « non soumise » : le verdict ne s'écrit jamais directement.
drop policy "souhaits ajoutés par les parents, idées par les autres" on public.wish_items;
create policy "souhaits ajoutés par les parents, idées par les autres" on public.wish_items
  for insert to authenticated
  with check (
    created_by = auth.uid()
    and (
      (kind = 'wish' and private.is_parent_of(child_id) and review_status = 'none')
      or (kind = 'idea' and private.can_view_child(child_id) and not private.is_parent_of(child_id)
          and review_status in ('none', 'pending'))
    )
  );

drop policy "cadeaux modifiés par qui peut les gérer" on public.wish_items;
create policy "cadeaux modifiés par qui peut les gérer" on public.wish_items
  for update to authenticated
  using (private.can_edit_item(id))
  with check (
    (kind = 'wish' and private.is_parent_of(child_id))
    or (kind = 'idea' and created_by = auth.uid() and private.can_view_child(child_id)
        and not private.is_parent_of(child_id)
        and review_status in ('none', 'pending'))
  );

-- ---------------------------------------------------------------------------
-- child_items : statut de validation renvoyé à l'app.
-- ---------------------------------------------------------------------------

drop function public.child_items(uuid, uuid, text);
create function public.child_items(p_child uuid, p_event uuid default null, p_kind text default null)
returns table (
  id uuid, child_id uuid, event_id uuid, kind text, title text, notes text, image_url text,
  priority smallint, "position" integer, owned boolean, created_by uuid, created_at timestamptz,
  status text, my_reservation text,
  pot_total numeric, pot_currency text, pot_count integer, my_contribution numeric,
  review_status text, review_note text
)
language sql stable security definer set search_path = '' as $$
  select i.id, i.child_id, i.event_id, i.kind, i.title, i.notes, i.image_url,
         i.priority, i.position, i.owned, i.created_by, i.created_at,
         case
           when parent.is_parent then null
           when i.owned then 'owned'
           when pot.count > 0 then 'pot'
           when r.user_id is null then 'available'
           when r.user_id = auth.uid() then 'mine'
           else 'taken'
         end,
         case when r.user_id = auth.uid() then r.status end,
         case when parent.is_parent then null else pot.total end,
         case when parent.is_parent then null else pot.currency end,
         case when parent.is_parent then null else pot.count end,
         case when parent.is_parent then null else pot.mine end,
         i.review_status, i.review_note
  from public.wish_items i
  cross join lateral (select private.is_parent_of(i.child_id) as is_parent) parent
  left join public.reservations r on r.item_id = i.id
  left join lateral (
    select sum(c.amount) as total, min(c.currency) as currency, count(*)::int as count,
           sum(c.amount) filter (where c.user_id = auth.uid()) as mine
    from public.contributions c where c.item_id = i.id
  ) pot on true
  where i.child_id = p_child
    and private.can_view_child(i.child_id)
    -- Une idée soumise est faite pour être lue par les parents (#57).
    and (i.kind = 'wish' or not parent.is_parent or i.review_status <> 'none')
    and (i.review_status not in ('rejected', 'owned_already')
         or i.created_by = auth.uid() or parent.is_parent)
    and (p_kind is null or i.kind = p_kind)
    and (p_event is null or i.event_id = p_event or i.event_id is null)
  order by i.owned, i.priority desc, i.position, i.created_at;
$$;

-- ---------------------------------------------------------------------------
-- Verdict du parent
-- ---------------------------------------------------------------------------

-- Accepter fait entrer l'idée dans la liste de souhaits de l'enfant ; l'auteur reste crédité.
-- Refuser ou « il l'a déjà » la conserve, visible de son auteur et des parents seulement.
create function public.review_idea(p_item uuid, p_decision text, p_note text default null) returns text
language plpgsql volatile security definer set search_path = '' as $$
declare
  v_item public.wish_items;
begin
  if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
  if p_decision not in ('accepted', 'rejected', 'owned_already') then raise exception 'INVALID_DECISION'; end if;

  select * into v_item from public.wish_items where id = p_item;
  if v_item.id is null or v_item.kind <> 'idea' then raise exception 'ITEM_NOT_FOUND'; end if;
  -- Qui ne voit pas l'enfant ne doit pas même apprendre que ce cadeau existe.
  if not private.can_view_child(v_item.child_id) then raise exception 'ITEM_NOT_FOUND'; end if;
  if not private.is_parent_of(v_item.child_id) then raise exception 'NOT_A_PARENT'; end if;
  if v_item.review_status = 'none' then raise exception 'IDEA_NOT_SUBMITTED'; end if;

  update public.wish_items
  set review_status = p_decision,
      reviewed_by = auth.uid(),
      reviewed_at = now(),
      review_note = nullif(btrim(coalesce(p_note, '')), ''),
      -- Acceptée ou déjà possédée, l'idée entre dans la liste et suit les règles des souhaits.
      kind = case when p_decision = 'rejected' then kind else 'wish' end,
      owned = case when p_decision = 'owned_already' then true else owned end
  where id = p_item;

  return p_decision;
end;
$$;

-- Idées soumises en attente d'un verdict, pour les enfants dont je suis parent.
create function public.pending_ideas()
returns table (
  id uuid, child_id uuid, child_name text, title text, notes text, image_url text,
  created_by uuid, author_name text, created_at timestamptz
)
language sql stable security definer set search_path = '' as $$
  select i.id, i.child_id, c.first_name, i.title, i.notes, i.image_url,
         i.created_by, p.display_name, i.created_at
  from public.wish_items i
  join public.children c on c.id = i.child_id
  left join public.profiles p on p.id = i.created_by
  where i.kind = 'idea'
    and i.review_status = 'pending'
    and private.is_parent_of(i.child_id)
  order by i.created_at desc;
$$;

-- Nombre de personnes qui verront une idée non soumise : sert à prévenir quand il n'y en a aucune.
create function public.idea_audience(p_child uuid) returns integer
language sql stable security definer set search_path = '' as $$
  select count(distinct gm.user_id)::int
  from public.children c
  join public.household_groups hg on hg.household_id = c.household_id
  join public.group_members gm on gm.group_id = hg.group_id
  where c.id = p_child
    and gm.user_id <> auth.uid()
    and not exists (
      select 1 from public.household_members hm
      where hm.household_id = c.household_id and hm.user_id = gm.user_id
    );
$$;

-- ---------------------------------------------------------------------------
-- Droits
-- ---------------------------------------------------------------------------

revoke execute on function
  public.child_items(uuid, uuid, text),
  public.review_idea(uuid, text, text),
  public.pending_ideas(),
  public.idea_audience(uuid)
from public, anon;
grant execute on function
  public.child_items(uuid, uuid, text),
  public.review_idea(uuid, text, text),
  public.pending_ideas(),
  public.idea_audience(uuid)
to authenticated;

-- Photos de cadeaux et d'avatars d'enfants.
-- Bucket public en lecture (URLs non devinables), écriture dans son propre dossier <user_id>/…
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('images', 'images', true, 5242880, array['image/jpeg', 'image/png', 'image/heic', 'image/webp'])
on conflict (id) do nothing;

create policy "images : ajout dans son dossier" on storage.objects for insert to authenticated
  with check (bucket_id = 'images' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "images : modification de ses fichiers" on storage.objects for update to authenticated
  using (bucket_id = 'images' and owner_id = auth.uid()::text);
create policy "images : suppression de ses fichiers" on storage.objects for delete to authenticated
  using (bucket_id = 'images' and owner_id = auth.uid()::text);

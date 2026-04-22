alter table public.profiles enable row level security;
alter table public.swipes enable row level security;
alter table public.matches enable row level security;
alter table public.messages enable row level security;

drop policy if exists "profiles_select_authenticated" on public.profiles;
create policy "profiles_select_authenticated"
on public.profiles
for select
to authenticated
using (true);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
on public.profiles
for insert
to authenticated
with check (id = auth.uid());

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
on public.profiles
for update
to authenticated
using (id = auth.uid())
with check (id = auth.uid());

drop policy if exists "swipes_select_own" on public.swipes;
create policy "swipes_select_own"
on public.swipes
for select
to authenticated
using (swiper_id = auth.uid());

drop policy if exists "swipes_insert_own" on public.swipes;
create policy "swipes_insert_own"
on public.swipes
for insert
to authenticated
with check (swiper_id = auth.uid());

drop policy if exists "swipes_update_own" on public.swipes;
create policy "swipes_update_own"
on public.swipes
for update
to authenticated
using (swiper_id = auth.uid())
with check (swiper_id = auth.uid());

drop policy if exists "matches_select_participant" on public.matches;
create policy "matches_select_participant"
on public.matches
for select
to authenticated
using (auth.uid() = user_a_id or auth.uid() = user_b_id);

drop policy if exists "matches_insert_participant" on public.matches;
create policy "matches_insert_participant"
on public.matches
for insert
to authenticated
with check (auth.uid() = user_a_id or auth.uid() = user_b_id);

drop policy if exists "matches_update_participant" on public.matches;
create policy "matches_update_participant"
on public.matches
for update
to authenticated
using (auth.uid() = user_a_id or auth.uid() = user_b_id)
with check (auth.uid() = user_a_id or auth.uid() = user_b_id);

drop policy if exists "messages_select_participant" on public.messages;
create policy "messages_select_participant"
on public.messages
for select
to authenticated
using (
    exists (
        select 1
        from public.matches m
        where m.id = match_id
          and (m.user_a_id = auth.uid() or m.user_b_id = auth.uid())
    )
);

drop policy if exists "messages_insert_sender_participant" on public.messages;
create policy "messages_insert_sender_participant"
on public.messages
for insert
to authenticated
with check (
    sender_id = auth.uid()
    and exists (
        select 1
        from public.matches m
        where m.id = match_id
          and (m.user_a_id = auth.uid() or m.user_b_id = auth.uid())
    )
);

drop policy if exists "profile_photos_public_read" on storage.objects;
create policy "profile_photos_public_read"
on storage.objects
for select
to public
using (bucket_id = 'profile-photos');

drop policy if exists "profile_photos_insert_own_folder" on storage.objects;
create policy "profile_photos_insert_own_folder"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'profile-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
);

drop policy if exists "profile_photos_update_own_folder" on storage.objects;
create policy "profile_photos_update_own_folder"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'profile-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
)
with check (
    bucket_id = 'profile-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
);

drop policy if exists "profile_photos_delete_own_folder" on storage.objects;
create policy "profile_photos_delete_own_folder"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'profile-photos'
    and auth.uid()::text = (storage.foldername(name))[1]
);

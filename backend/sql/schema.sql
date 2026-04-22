create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    email text not null default '',
    name text not null default '',
    age integer not null default 0,
    city text not null default '',
    job_title text not null default '',
    headline text not null default '',
    about text not null default '',
    interests text[] not null default '{}',
    photo_urls text[] not null default '{}',
    created_at_ms bigint not null,
    updated_at_ms bigint not null
);

create table if not exists public.swipes (
    id text primary key,
    swiper_id uuid not null references auth.users (id) on delete cascade,
    swiped_id uuid not null references auth.users (id) on delete cascade,
    direction text not null check (direction in ('like', 'pass')),
    created_at_ms bigint not null,
    unique (swiper_id, swiped_id)
);

create table if not exists public.matches (
    id text primary key,
    user_a_id uuid not null references auth.users (id) on delete cascade,
    user_b_id uuid not null references auth.users (id) on delete cascade,
    matched_at_ms bigint not null,
    last_message text not null default '',
    last_sender_id uuid references auth.users (id) on delete set null,
    last_message_at_ms bigint not null default 0,
    check (user_a_id <> user_b_id)
);

create table if not exists public.messages (
    id text primary key,
    match_id text not null references public.matches (id) on delete cascade,
    sender_id uuid not null references auth.users (id) on delete cascade,
    text text not null,
    sent_at_ms bigint not null
);

create index if not exists profiles_updated_at_idx on public.profiles (updated_at_ms desc);
create index if not exists swipes_swiper_id_idx on public.swipes (swiper_id);
create index if not exists swipes_swiped_id_idx on public.swipes (swiped_id);
create index if not exists matches_user_a_id_idx on public.matches (user_a_id);
create index if not exists matches_user_b_id_idx on public.matches (user_b_id);
create index if not exists messages_match_id_sent_at_idx on public.messages (match_id, sent_at_ms);

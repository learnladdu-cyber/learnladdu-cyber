-- NoteSphere cloud schema, ownership policies and optimistic-concurrency push RPC.
-- Apply with `supabase db push` (or run this file in the Supabase SQL editor).
-- The anon key is safe for the app; no service-role key is used by Android.

create extension if not exists pgcrypto;

create sequence if not exists public.sync_revision_seq;

create or replace function public.bump_sync_revision()
returns trigger
language plpgsql
as $$
begin
    new.server_revision := nextval('public.sync_revision_seq');
    return new;
end;
$$;

create table if not exists public.folders (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    color integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists folders_owner_idx on public.folders (owner_id);
create index if not exists folders_pull_idx on public.folders (server_revision);
create unique index if not exists folders_active_name_idx
    on public.folders (owner_id, lower(name)) where deleted_at is null;

create table if not exists public.tags (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    name text not null,
    color integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists tags_owner_idx on public.tags (owner_id);
create index if not exists tags_pull_idx on public.tags (server_revision);
create unique index if not exists tags_active_name_idx
    on public.tags (owner_id, lower(name)) where deleted_at is null;

create table if not exists public.notes (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    title text not null default '',
    content text not null default '',
    folder_id uuid references public.folders(id) on delete set null,
    is_pinned boolean not null default false,
    is_archived boolean not null default false,
    is_trashed boolean not null default false,
    trashed_at timestamptz,
    reminder_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists notes_owner_idx on public.notes (owner_id);
create index if not exists notes_pull_idx on public.notes (server_revision);
create index if not exists notes_folder_idx on public.notes (folder_id);
create index if not exists notes_updated_idx on public.notes (owner_id, updated_at desc);

create table if not exists public.checklist_items (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    note_id uuid not null references public.notes(id) on delete cascade,
    text text not null,
    is_checked boolean not null default false,
    position integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists checklist_owner_idx on public.checklist_items (owner_id);
create index if not exists checklist_note_idx on public.checklist_items (note_id);
create index if not exists checklist_pull_idx on public.checklist_items (server_revision);

create table if not exists public.note_tags (
    note_id uuid not null references public.notes(id) on delete cascade,
    tag_id uuid not null references public.tags(id) on delete cascade,
    owner_id uuid not null references auth.users(id) on delete cascade,
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq'),
    primary key (note_id, tag_id)
);
create index if not exists note_tags_owner_idx on public.note_tags (owner_id);
create index if not exists note_tags_tag_idx on public.note_tags (tag_id);
create index if not exists note_tags_pull_idx on public.note_tags (server_revision);

create table if not exists public.attachments (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    note_id uuid references public.notes(id) on delete set null,
    display_name text not null,
    mime_type text not null,
    kind text not null,
    size_bytes bigint not null default 0,
    duration_ms bigint,
    waveform text,
    storage_path text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists attachments_owner_idx on public.attachments (owner_id);
create index if not exists attachments_note_idx on public.attachments (note_id);
create index if not exists attachments_pull_idx on public.attachments (server_revision);

create table if not exists public.transcriptions (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    note_id uuid references public.notes(id) on delete cascade,
    attachment_id uuid references public.attachments(id) on delete cascade,
    language text not null,
    engine_id text not null,
    text text not null default '',
    status text not null default 'PENDING',
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 1 check (version > 0),
    deleted_at timestamptz,
    server_revision bigint not null default nextval('public.sync_revision_seq')
);
create index if not exists transcriptions_owner_idx on public.transcriptions (owner_id);
create index if not exists transcriptions_attachment_idx on public.transcriptions (attachment_id);
create index if not exists transcriptions_pull_idx on public.transcriptions (server_revision);

create table if not exists public.devices (
    id text primary key,
    owner_id uuid not null references auth.users(id) on delete cascade,
    device_id text not null,
    device_name text not null,
    platform text not null default 'android',
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (owner_id, device_id)
);
create index if not exists devices_owner_idx on public.devices (owner_id);

drop trigger if exists folders_sync_revision on public.folders;
create trigger folders_sync_revision before insert or update on public.folders
for each row execute function public.bump_sync_revision();
drop trigger if exists tags_sync_revision on public.tags;
create trigger tags_sync_revision before insert or update on public.tags
for each row execute function public.bump_sync_revision();
drop trigger if exists notes_sync_revision on public.notes;
create trigger notes_sync_revision before insert or update on public.notes
for each row execute function public.bump_sync_revision();
drop trigger if exists checklist_sync_revision on public.checklist_items;
create trigger checklist_sync_revision before insert or update on public.checklist_items
for each row execute function public.bump_sync_revision();
drop trigger if exists note_tags_sync_revision on public.note_tags;
create trigger note_tags_sync_revision before insert or update on public.note_tags
for each row execute function public.bump_sync_revision();
drop trigger if exists attachments_sync_revision on public.attachments;
create trigger attachments_sync_revision before insert or update on public.attachments
for each row execute function public.bump_sync_revision();
drop trigger if exists transcriptions_sync_revision on public.transcriptions;
create trigger transcriptions_sync_revision before insert or update on public.transcriptions
for each row execute function public.bump_sync_revision();

alter table public.folders enable row level security;
alter table public.tags enable row level security;
alter table public.notes enable row level security;
alter table public.checklist_items enable row level security;
alter table public.note_tags enable row level security;
alter table public.attachments enable row level security;
alter table public.transcriptions enable row level security;
alter table public.devices enable row level security;

drop policy if exists "owner folders" on public.folders;
create policy "owner folders" on public.folders for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner tags" on public.tags;
create policy "owner tags" on public.tags for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner notes" on public.notes;
create policy "owner notes" on public.notes for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner checklist" on public.checklist_items;
create policy "owner checklist" on public.checklist_items for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner note tags" on public.note_tags;
create policy "owner note tags" on public.note_tags for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner attachments" on public.attachments;
create policy "owner attachments" on public.attachments for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner transcriptions" on public.transcriptions;
create policy "owner transcriptions" on public.transcriptions for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());
drop policy if exists "owner devices" on public.devices;
create policy "owner devices" on public.devices for all to authenticated
using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create or replace function public.sync_push(
    p_entity_type text,
    p_entity_id uuid,
    p_operation text,
    p_base_version bigint,
    p_payload jsonb
)
returns jsonb
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    v_uid uuid := auth.uid();
    v_row record;
    v_version bigint;
    v_result jsonb;
begin
    if v_uid is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    if p_operation not in ('UPSERT', 'DELETE') then
        raise exception 'invalid operation' using errcode = '22023';
    end if;

    if p_entity_type = 'FOLDER' then
        select * into v_row from public.folders where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.folders (id, owner_id, name, color, created_at, updated_at, deleted_at)
            values (
                p_entity_id, v_uid,
                coalesce(p_payload->>'name', 'Folder'),
                coalesce((p_payload->>'color')::integer, 0),
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.folders set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.folders set
                name = coalesce(p_payload->>'name', name),
                color = coalesce((p_payload->>'color')::integer, color),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.folders where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'TAG' then
        select * into v_row from public.tags where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.tags (id, owner_id, name, color, created_at, updated_at, deleted_at)
            values (
                p_entity_id, v_uid,
                coalesce(p_payload->>'name', 'Tag'),
                coalesce((p_payload->>'color')::integer, 0),
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.tags set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.tags set
                name = coalesce(p_payload->>'name', name),
                color = coalesce((p_payload->>'color')::integer, color),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.tags where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'NOTE' then
        if (p_payload->>'folder_id') is not null and not exists (
            select 1 from public.folders
            where id = (p_payload->>'folder_id')::uuid and owner_id = v_uid
        ) then
            raise exception 'folder not found' using errcode = '42501';
        end if;
        select * into v_row from public.notes where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.notes (
                id, owner_id, title, content, folder_id, is_pinned, is_archived,
                is_trashed, trashed_at, reminder_at, created_at, updated_at, deleted_at
            ) values (
                p_entity_id, v_uid,
                coalesce(p_payload->>'title', ''),
                coalesce(p_payload->>'content', ''),
                (p_payload->>'folder_id')::uuid,
                coalesce((p_payload->>'is_pinned')::boolean, false),
                coalesce((p_payload->>'is_archived')::boolean, false),
                coalesce((p_payload->>'is_trashed')::boolean, false),
                (p_payload->>'trashed_at')::timestamptz,
                (p_payload->>'reminder_at')::timestamptz,
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.notes set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.notes set
                title = coalesce(p_payload->>'title', title),
                content = coalesce(p_payload->>'content', content),
                folder_id = (p_payload->>'folder_id')::uuid,
                is_pinned = coalesce((p_payload->>'is_pinned')::boolean, is_pinned),
                is_archived = coalesce((p_payload->>'is_archived')::boolean, is_archived),
                is_trashed = coalesce((p_payload->>'is_trashed')::boolean, is_trashed),
                trashed_at = (p_payload->>'trashed_at')::timestamptz,
                reminder_at = (p_payload->>'reminder_at')::timestamptz,
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.notes where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'CHECKLIST' then
        select * into v_row from public.checklist_items where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.checklist_items (
                id, owner_id, note_id, text, is_checked, position,
                created_at, updated_at, deleted_at
            ) values (
                p_entity_id, v_uid,
                (p_payload->>'note_id')::uuid,
                coalesce(p_payload->>'text', ''),
                coalesce((p_payload->>'is_checked')::boolean, false),
                coalesce((p_payload->>'position')::integer, 0),
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.checklist_items set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.checklist_items set
                text = coalesce(p_payload->>'text', text),
                is_checked = coalesce((p_payload->>'is_checked')::boolean, is_checked),
                position = coalesce((p_payload->>'position')::integer, position),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.checklist_items where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'ATTACHMENT' then
        select * into v_row from public.attachments where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.attachments (
                id, owner_id, note_id, display_name, mime_type, kind, size_bytes,
                duration_ms, waveform, storage_path, created_at, updated_at, deleted_at
            ) values (
                p_entity_id, v_uid,
                (p_payload->>'note_id')::uuid,
                coalesce(p_payload->>'display_name', 'Attachment'),
                coalesce(p_payload->>'mime_type', 'application/octet-stream'),
                coalesce(p_payload->>'kind', 'DOCUMENT'),
                coalesce((p_payload->>'size_bytes')::bigint, 0),
                (p_payload->>'duration_ms')::bigint,
                p_payload->>'waveform',
                p_payload->>'storage_path',
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.attachments set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.attachments set
                note_id = (p_payload->>'note_id')::uuid,
                display_name = coalesce(p_payload->>'display_name', display_name),
                mime_type = coalesce(p_payload->>'mime_type', mime_type),
                kind = coalesce(p_payload->>'kind', kind),
                size_bytes = coalesce((p_payload->>'size_bytes')::bigint, size_bytes),
                duration_ms = (p_payload->>'duration_ms')::bigint,
                waveform = p_payload->>'waveform',
                storage_path = coalesce(p_payload->>'storage_path', storage_path),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.attachments where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'TRANSCRIPTION' then
        select * into v_row from public.transcriptions where id = p_entity_id for update;
        if not found then
            if p_base_version > 0 then
                return jsonb_build_object('status', 'conflict', 'remote', null);
            end if;
            insert into public.transcriptions (
                id, owner_id, note_id, attachment_id, language, engine_id, text,
                status, error_message, created_at, updated_at, deleted_at
            ) values (
                p_entity_id, v_uid,
                (p_payload->>'note_id')::uuid,
                (p_payload->>'attachment_id')::uuid,
                coalesce(p_payload->>'language', 'ENGLISH'),
                coalesce(p_payload->>'engine_id', 'unknown'),
                coalesce(p_payload->>'text', ''),
                coalesce(p_payload->>'status', 'PENDING'),
                p_payload->>'error_message',
                coalesce((p_payload->>'created_at')::timestamptz, now()),
                coalesce((p_payload->>'updated_at')::timestamptz, now()),
                (p_payload->>'deleted_at')::timestamptz
            );
            return jsonb_build_object('status', 'applied', 'version', 1);
        end if;
        if v_row.owner_id <> v_uid then
            raise exception 'not found' using errcode = '42501';
        end if;
        if v_row.version <> p_base_version then
            return jsonb_build_object('status', 'conflict', 'remote', to_jsonb(v_row));
        end if;
        if p_operation = 'DELETE' then
            update public.transcriptions set deleted_at = coalesce((p_payload->>'deleted_at')::timestamptz, now()),
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                version = version + 1
            where id = p_entity_id;
        else
            update public.transcriptions set
                text = coalesce(p_payload->>'text', text),
                language = coalesce(p_payload->>'language', language),
                engine_id = coalesce(p_payload->>'engine_id', engine_id),
                status = coalesce(p_payload->>'status', status),
                error_message = p_payload->>'error_message',
                updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
                deleted_at = (p_payload->>'deleted_at')::timestamptz,
                version = version + 1
            where id = p_entity_id;
        end if;
        select version into v_version from public.transcriptions where id = p_entity_id;
        return jsonb_build_object('status', 'applied', 'version', v_version);
    end if;

    if p_entity_type = 'NOTE_TAGS' then
        if not exists (select 1 from public.notes where id = p_entity_id and owner_id = v_uid) then
            raise exception 'note not found' using errcode = '42501';
        end if;
        update public.note_tags
        set deleted_at = coalesce((p_payload->>'updated_at')::timestamptz, now()),
            updated_at = coalesce((p_payload->>'updated_at')::timestamptz, now())
        where note_id = p_entity_id and owner_id = v_uid
          and deleted_at is null
          and tag_id not in (
              select value::uuid from jsonb_array_elements_text(
                  coalesce(p_payload->'tag_ids', '[]'::jsonb)
              )
          );
        insert into public.note_tags (note_id, tag_id, owner_id, updated_at, deleted_at)
        select p_entity_id, t.id, v_uid,
            coalesce((p_payload->>'updated_at')::timestamptz, now()), null
        from public.tags t
        where t.owner_id = v_uid
          and t.id in (
              select value::uuid from jsonb_array_elements_text(
                  coalesce(p_payload->'tag_ids', '[]'::jsonb)
              )
          )
        on conflict (note_id, tag_id) do update
        set owner_id = excluded.owner_id,
            updated_at = excluded.updated_at,
            deleted_at = null;
        return jsonb_build_object('status', 'applied', 'version', 0);
    end if;

    raise exception 'unsupported entity type: %', p_entity_type using errcode = '22023';
end;
$$;

revoke all on function public.sync_push(text, uuid, text, bigint, jsonb) from public;
grant execute on function public.sync_push(text, uuid, text, bigint, jsonb) to authenticated;

insert into storage.buckets (id, name, public, file_size_limit)
values ('notesphare-attachments', 'notesphare-attachments', false, 52428800)
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit;

drop policy if exists "owner attachment objects" on storage.objects;
create policy "owner attachment objects" on storage.objects for all to authenticated
using (
    bucket_id = 'notesphare-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
)
with check (
    bucket_id = 'notesphare-attachments'
    and (storage.foldername(name))[1] = auth.uid()::text
);

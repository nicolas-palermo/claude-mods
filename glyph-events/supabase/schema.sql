-- Glyph Events: esquema de Supabase. Pegalo entero en SQL Editor > New query > Run.
-- Se puede correr más de una vez: no borra nada.
-- Todas las tablas tienen RLS sin políticas: solo el backend (service role) puede leerlas.

create extension if not exists pgcrypto;

-- Una sola fila: el teléfono, la ventana de agrupamiento y los horarios de silencio.
create table if not exists ge_settings (
  id int primary key default 1 check (id = 1),
  device_key text not null default encode(gen_random_bytes(18), 'hex'),
  fcm_token text,
  device_model text,
  app_version text,
  device_registered_at timestamptz,
  batch_window_seconds int not null default 8 check (batch_window_seconds between 0 and 50),
  quiet_enabled boolean not null default false,
  quiet_start time not null default '23:00',
  quiet_end time not null default '08:00',
  timezone text not null default 'America/Argentina/Buenos_Aires'
);
insert into ge_settings (id) values (1) on conflict (id) do nothing;

-- Cada servicio que manda webhooks tiene su URL con token propio.
create table if not exists ge_sources (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  token text not null unique default encode(gen_random_bytes(18), 'hex'),
  created_at timestamptz not null default now()
);

create table if not exists ge_sounds (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  path text not null,
  mime text,
  size int,
  created_at timestamptz not null default now()
);

-- data: {"frames": ["169 caracteres", ...], "frameMs": 100, "repeat": 1, "brightness": 100}
create table if not exists ge_animations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  data jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- conditions: [{"field": "headers.x-github-event", "op": "equals", "value": "push"}, ...] (todas deben cumplirse)
create table if not exists ge_rules (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  enabled boolean not null default true,
  priority int not null default 0,
  source_id uuid references ge_sources (id) on delete cascade,
  conditions jsonb not null default '[]',
  animation_id uuid not null references ge_animations (id) on delete cascade,
  sound_id uuid references ge_sounds (id) on delete set null,
  created_at timestamptz not null default now()
);

create table if not exists ge_batches (
  id uuid primary key default gen_random_uuid(),
  status text not null default 'open', -- open | resolving | done
  opened_at timestamptz not null default now(),
  deadline timestamptz not null,
  resolved_at timestamptz,
  result text,
  play_id uuid
);
create index if not exists ge_batches_open on ge_batches (status, deadline);

create table if not exists ge_events (
  id uuid primary key default gen_random_uuid(),
  received_at timestamptz not null default now(),
  source_id uuid references ge_sources (id) on delete set null,
  source_name text,
  method text,
  headers jsonb,
  query jsonb,
  body jsonb,
  batch_id uuid references ge_batches (id) on delete set null
);
create index if not exists ge_events_received on ge_events (received_at desc);
create index if not exists ge_events_batch on ge_events (batch_id);

-- Lo que se mandó (o no) al teléfono.
create table if not exists ge_plays (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  kind text not null, -- rule | test
  batch_id uuid references ge_batches (id) on delete set null,
  rule_id uuid references ge_rules (id) on delete set null,
  rule_name text,
  animation_id uuid references ge_animations (id) on delete set null,
  animation_name text,
  sound_id uuid references ge_sounds (id) on delete set null,
  status text not null, -- sent | silenced | no_device | error | played | play_error
  detail text,
  payload jsonb,
  acked_at timestamptz
);
create index if not exists ge_plays_created on ge_plays (created_at desc);

alter table ge_settings enable row level security;
alter table ge_sources enable row level security;
alter table ge_sounds enable row level security;
alter table ge_animations enable row level security;
alter table ge_rules enable row level security;
alter table ge_batches enable row level security;
alter table ge_events enable row level security;
alter table ge_plays enable row level security;

-- Mete el evento en el lote abierto, o abre uno nuevo que cierra en p_window segundos.
-- El lock evita que dos webhooks simultáneos abran dos lotes.
create or replace function ge_attach_event(p_event uuid, p_window int)
returns ge_batches language plpgsql as $$
declare b ge_batches;
begin
  perform pg_advisory_xact_lock(727274);
  select * into b from ge_batches
    where status = 'open' and deadline > now()
    order by opened_at desc limit 1;
  if not found then
    insert into ge_batches (deadline) values (now() + make_interval(secs => p_window)) returning * into b;
  end if;
  update ge_events set batch_id = b.id where id = p_event;
  return b;
end $$;

-- Toma (una sola vez) los lotes cuyo plazo ya venció.
create or replace function ge_claim_due_batches()
returns setof ge_batches language sql as $$
  update ge_batches set status = 'resolving'
  where status = 'open' and deadline <= now()
  returning *;
$$;

revoke all on function ge_attach_event(uuid, int) from public, anon, authenticated;
revoke all on function ge_claim_due_batches() from public, anon, authenticated;

-- Bucket privado para los sonidos; el teléfono los baja con URLs firmadas.
insert into storage.buckets (id, name, public) values ('ge-sounds', 'ge-sounds', false)
on conflict (id) do nothing;

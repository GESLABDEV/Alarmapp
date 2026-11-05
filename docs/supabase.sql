-- Tabla para los resúmenes anónimos (opcional, con consentimiento).
-- Ejecutar en el SQL Editor de Supabase (plan gratuito).

create table if not exists public.night_summaries (
  id              bigint generated always as identity primary key,
  received_at     timestamptz not null default now(),
  app_version     text,
  mode            text check (mode in ('FIXED', 'RECOMMENDED', 'INVERSE')),
  bed_hour        smallint check (bed_hour between 0 and 23),
  total_sleep_min integer,
  latency_min     integer,
  light_min       integer,
  deep_min        integer,
  rem_min         integer,
  awake_min       integer,
  wake_stage      text check (wake_stage in ('AWAKE', 'LIGHT', 'DEEP', 'REM')),
  window_min      smallint check (window_min between 10 and 45),
  snoozes         smallint check (snoozes between 0 and 2),
  rating          smallint check (rating between 1 and 5)
);

-- La app solo puede INSERTAR. Nadie con la clave pública puede leer, editar ni borrar.
alter table public.night_summaries enable row level security;

create policy "anon puede insertar resúmenes"
  on public.night_summaries for insert
  to anon
  with check (true);

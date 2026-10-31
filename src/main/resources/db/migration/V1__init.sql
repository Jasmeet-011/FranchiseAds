-- Initial schema: brands, their locations and campaigns, and generated ad variants.
-- String lists are text[] (ADR-007). IDs are identity columns, timestamps are timestamptz.

create table brand (
    id               bigint generated always as identity primary key,
    name             varchar(100)  not null,
    voice_guidelines varchar(2000),
    banned_words     text[]        not null default '{}',
    created_at       timestamptz   not null
);

-- Brand names are unique ignoring case: "Acme" and "acme" conflict (ADR-015).
create unique index uq_brand_name_lower on brand (lower(name));

create table location (
    id          bigint generated always as identity primary key,
    brand_id    bigint       not null constraint fk_location_brand references brand (id),
    name        varchar(100) not null,
    city        varchar(100) not null,
    region      varchar(100),
    local_offer varchar(200),
    created_at  timestamptz  not null
);

create index ix_location_brand_id on location (brand_id);

create table campaign (
    id         bigint generated always as identity primary key,
    brand_id   bigint       not null constraint fk_campaign_brand references brand (id),
    name       varchar(100) not null,
    theme      varchar(500) not null,
    created_at timestamptz  not null
);

create index ix_campaign_brand_id on campaign (brand_id);

create table ad_variant (
    id                  bigint generated always as identity primary key,
    campaign_id         bigint       not null constraint fk_ad_variant_campaign references campaign (id),
    location_id         bigint       not null constraint fk_ad_variant_location references location (id),
    headlines           text[]       not null,
    descriptions        text[]       not null,
    status              varchar(20)  not null
        constraint ck_ad_variant_status check (status in ('DRAFT', 'PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    validation_passed   boolean      not null,
    violations          text[]       not null default '{}',
    generation_attempts integer      not null
        constraint ck_ad_variant_generation_attempts check (generation_attempts between 1 and 2),
    rejection_reason    varchar(500),
    model               varchar(100) not null,
    version             bigint       not null default 0,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null
);

create index ix_ad_variant_campaign_id on ad_variant (campaign_id);
create index ix_ad_variant_location_id on ad_variant (location_id);

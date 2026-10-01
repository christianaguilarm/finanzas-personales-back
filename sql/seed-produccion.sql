-- ============================================================================
-- seed-produccion.sql
--
-- RESTAURA la base de datos de produccion a partir de una copia de tu base
-- LOCAL de desarrollo. Generado con pg_dump 15.19 (PostgreSQL 15).
--
-- Incluye esquema Y datos: 355 transacciones, 5 cuentas, 20 categorias.
--
-- CUIDADO: este script BORRA las tablas existentes antes de recrearlas
-- (sentencias DROP TABLE ... CASCADE generadas por pg_dump --clean).
-- Solo usalo si no hay datos que te importen.
--
-- ============================================================================
-- COMO USARLO
-- ============================================================================
--
--   heroku pg:psql -a <APP> < sql/seed-produccion.sql
--
-- Reemplaza <APP> por el nombre de tu app de Heroku.
--
-- Si preferis que Heroku lo ejecute por vos:
--
--   heroku pg:psql -a <APP> -f sql/seed-produccion.sql
--
-- ============================================================================
-- PENDIENTE ANTES DE DESPLEGAR
-- ============================================================================
--
-- 1. auth0_sub quedo en NULL a proposito. Se elimino el valor de dev
--    (NULL) porque NO sirve en produccion: el
--    tenant de prod emite otro sub. Antes del primer login tenes que setearlo:
--
--      heroku pg:psql -a <APP> -c \
--        "UPDATE app_user SET auth0_sub = 'auth0|TU_SUB_DE_PROD' WHERE id = 1;"
--
--    Si lo dejas en NULL, el primer login crea un usuario NUEVO y vacio y
--    estas 355 transacciones quedan inaccesibles.
--
-- 2. Variables de entorno en Vercel (solo Production):
--      API_URL         = https://<APP>.herokuapp.com/api
--      AUTH0_DOMAIN    = finanzas-personales-prod.us.auth0.com
--      AUTH0_CLIENT_ID = <Client ID de la SPA del tenant prod>
--      AUTH0_AUDIENCE  = https://api.finanzas-personales
--
-- 3. En Auth0 (tenant PROD), tu Application SPA:
--      Allowed Callback URLs: https://finanzas-personales-ui.vercel.app/callback
--      Allowed Logout URLs:   https://finanzas-personales-ui.vercel.app
--      Allowed Web Origins:   https://finanzas-personales-ui.vercel.app
--
-- 4. Desplegar el backend (desde finanzas-personales-back):
--      git push heroku develop:main
--
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Limpieza previa (idempotente: se puede correr sobre una base vacia o una
-- base con el esquema viejo). El orden va de tablas hijas a padres.
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS public.transaccion_tag CASCADE;
DROP TABLE IF EXISTS public.transaccion     CASCADE;
DROP TABLE IF EXISTS public.tag              CASCADE;
DROP TABLE IF EXISTS public.cuenta           CASCADE;
DROP TABLE IF EXISTS public.comercio         CASCADE;
DROP TABLE IF EXISTS public.categoria        CASCADE;
DROP TABLE IF EXISTS public.app_user         CASCADE;

-- Las secuencias quedan colgando si solo dropeamos las tablas.
DROP SEQUENCE IF EXISTS public.transaccion_id_seq;
DROP SEQUENCE IF EXISTS public.tag_id_seq;
DROP SEQUENCE IF EXISTS public.cuenta_id_seq;
DROP SEQUENCE IF EXISTS public.comercio_id_seq;
DROP SEQUENCE IF EXISTS public.categoria_id_seq;
DROP SEQUENCE IF EXISTS public.app_user_id_seq;

--
-- PostgreSQL database dump
--

\restrict vViWXysj2QglTbYZetHQ2u8RG8ksQNRk6f6MEwUL0qFPnqvAllCEp3sw0d4SKfq

-- Dumped from database version 15.19 (Debian 15.19-1.pgdg13+2)
-- Dumped by pg_dump version 15.19 (Debian 15.19-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_user (
    id bigint NOT NULL,
    creado_en timestamp(6) without time zone NOT NULL,
    email character varying(150) NOT NULL,
    nombre character varying(300),
    password_hash text,
    auth0_sub character varying(64)
);


--
-- Name: COLUMN app_user.auth0_sub; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.app_user.auth0_sub IS 'Identificador de Auth0 (sub del JWT). Clave unica de identidad; el email ya no identifica.';


--
-- Name: app_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categoria; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categoria (
    id bigint NOT NULL,
    color character varying(10),
    icono character varying(40),
    nombre character varying(80) NOT NULL,
    tipo character varying(255) NOT NULL,
    parent_id bigint,
    user_id bigint NOT NULL,
    CONSTRAINT categoria_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['INGRESO'::character varying, 'EGRESO'::character varying, 'TRANSFERENCIA'::character varying])::text[])))
);


--
-- Name: categoria_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: comercio; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.comercio (
    id bigint NOT NULL,
    nombre character varying(120) NOT NULL,
    rubro character varying(80),
    user_id bigint NOT NULL
);


--
-- Name: comercio_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.comercio_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cuenta; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cuenta (
    id bigint NOT NULL,
    activo boolean NOT NULL,
    moneda character varying(3) NOT NULL,
    nombre character varying(80) NOT NULL,
    saldo_inicial numeric(14,2) NOT NULL,
    tipo character varying(40) NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: cuenta_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cuenta_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tag (
    id bigint NOT NULL,
    nombre character varying(40) NOT NULL,
    user_id bigint NOT NULL
);


--
-- Name: tag_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.tag_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaccion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaccion (
    id bigint NOT NULL,
    creado_en timestamp(6) without time zone NOT NULL,
    cuota_actual integer,
    descripcion character varying(300),
    es_recurrente boolean NOT NULL,
    fecha date NOT NULL,
    medio character varying(255) NOT NULL,
    monto numeric(14,2) NOT NULL,
    periodo_facturacion character varying(7),
    tipo character varying(255) NOT NULL,
    total_cuotas integer,
    categoria_id bigint,
    comercio_id bigint,
    cuenta_id bigint NOT NULL,
    subcategoria_id bigint,
    user_id bigint NOT NULL,
    CONSTRAINT transaccion_medio_check CHECK (((medio)::text = ANY ((ARRAY['EFECTIVO'::character varying, 'DEBITO'::character varying, 'CREDITO'::character varying, 'PREPAGO'::character varying, 'TRANSFERENCIA'::character varying, 'OTRO'::character varying])::text[]))),
    CONSTRAINT transaccion_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['INGRESO'::character varying, 'EGRESO'::character varying, 'TRANSFERENCIA'::character varying])::text[])))
);


--
-- Name: transaccion_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.transaccion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: transaccion_tag; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaccion_tag (
    transaccion_id bigint NOT NULL,
    tag_id bigint NOT NULL
);


--
-- Data for Name: app_user; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.app_user VALUES
	(1, '2026-04-24 16:14:58.153716', 'christianaguilarm1@gmail.com', 'Christian Aguilar', '$2a$10$JCm.4cRf1lrY76jyPEQbUOkcsyOI5N.7Baxe/h/ioFkhUN0fODFae', 'NULL');


--
-- Data for Name: categoria; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.categoria VALUES
	(1, NULL, NULL, 'Gastos Obligatorios', 'EGRESO', NULL, 1),
	(2, NULL, NULL, 'Compras', 'EGRESO', NULL, 1),
	(34, NULL, NULL, 'Gastos bancarios', 'EGRESO', NULL, 1),
	(35, NULL, NULL, 'Deuda', 'EGRESO', NULL, 1),
	(68, NULL, NULL, 'Transporte', 'EGRESO', NULL, 1),
	(69, NULL, NULL, 'Prestado', 'EGRESO', NULL, 1),
	(70, NULL, NULL, 'Animales', 'EGRESO', NULL, 1),
	(74, NULL, NULL, 'Suscripciones', 'EGRESO', NULL, 1),
	(75, NULL, NULL, 'Sueldo', 'INGRESO', NULL, 1),
	(76, NULL, NULL, 'Devuelto', 'INGRESO', NULL, 1),
	(79, NULL, NULL, 'Mercadería', 'EGRESO', NULL, 1),
	(80, NULL, NULL, 'Salud', 'EGRESO', NULL, 1),
	(81, NULL, NULL, 'Juegos', 'EGRESO', NULL, 1),
	(82, NULL, NULL, 'Planes', 'EGRESO', NULL, 1),
	(77, NULL, NULL, 'Uber', 'EGRESO', 68, 1),
	(71, NULL, NULL, 'Pan', 'EGRESO', 79, 1),
	(78, NULL, NULL, 'Negocio', 'EGRESO', 79, 1),
	(83, NULL, NULL, 'Gustos', 'EGRESO', NULL, 1),
	(72, NULL, NULL, 'Bebida', 'EGRESO', 83, 1),
	(67, NULL, NULL, 'Comer afuera', 'EGRESO', 83, 1);


--
-- Data for Name: comercio; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: cuenta; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.cuenta VALUES
	(1, true, 'CLP', 'Tarjeta Banco Falabella', 0.00, 'CREDITO', 1),
	(36, true, 'CLP', 'Tarjeta Visa Platinum', 0.00, 'CREDITO', 1),
	(37, true, 'CLP', 'TC Fan Banco de Chile', 0.00, 'CREDITO', 1),
	(35, true, 'CLP', 'CC Banco Chile', 318177.00, 'DEBITO', 1),
	(34, true, 'CLP', 'TC Tenpo', 0.00, 'CREDITO', 1);


--
-- Data for Name: tag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: transaccion; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.transaccion VALUES
	(3, '2026-04-24 16:28:50.970356', 3, '', false, '2026-03-01', 'CREDITO', 5959.00, '2026-05', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(34, '2026-04-30 12:14:19.684912', 2, 'Audifonos ML', false, '2026-04-02', 'CREDITO', 28486.00, '2026-05', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(67, '2026-05-04 09:43:55.413823', NULL, 'Uber asado cabros', false, '2026-04-19', 'EFECTIVO', 14958.00, '2026-05', 'EGRESO', NULL, 69, NULL, 1, NULL, 1),
	(68, '2026-05-04 09:44:25.021049', NULL, 'Uber asado super', false, '2026-04-19', 'EFECTIVO', 1813.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(69, '2026-05-04 09:45:37.574367', NULL, '', false, '2026-04-19', 'EFECTIVO', 3500.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(70, '2026-05-04 09:46:03.595896', NULL, 'Uber asado cabros', false, '2026-04-19', 'EFECTIVO', 16875.00, '2026-05', 'EGRESO', NULL, 69, NULL, 1, NULL, 1),
	(71, '2026-05-04 09:47:05.59471', NULL, '', false, '2026-04-19', 'EFECTIVO', 16000.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(72, '2026-05-04 10:52:10.03761', NULL, 'Uber ida feria otaku cerrillos', false, '2026-04-20', 'EFECTIVO', 5150.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(73, '2026-05-04 10:53:16.657601', NULL, 'Scooter asado departamental a super10', false, '2026-04-20', 'EFECTIVO', 1550.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(74, '2026-05-04 10:55:18.206268', NULL, 'Arena + bolsas', false, '2026-04-21', 'EFECTIVO', 31220.00, '2026-05', 'EGRESO', NULL, 70, NULL, 1, NULL, 1),
	(75, '2026-05-04 10:55:44.008391', NULL, '', false, '2026-04-21', 'EFECTIVO', 4550.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(76, '2026-05-04 10:56:40.488498', NULL, '', false, '2026-04-21', 'EFECTIVO', 2130.00, '2026-05', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(77, '2026-05-04 11:01:25.836271', NULL, 'Apuesta', false, '2026-04-22', 'EFECTIVO', 10000.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(78, '2026-05-04 11:01:58.499948', NULL, '', false, '2026-04-22', 'EFECTIVO', 5450.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(79, '2026-05-04 11:02:26.768827', NULL, 'Comida barf', false, '2026-04-23', 'EFECTIVO', 27900.00, '2026-05', 'EGRESO', NULL, 70, NULL, 1, NULL, 1),
	(80, '2026-05-04 11:06:01.771611', NULL, 'Apuesta', false, '2026-04-23', 'EFECTIVO', 5000.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(81, '2026-05-04 11:06:38.635855', NULL, '', false, '2026-04-23', 'EFECTIVO', 4290.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(82, '2026-05-04 11:06:56.950587', NULL, 'KFC', false, '2026-04-23', 'EFECTIVO', 21490.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(83, '2026-05-04 16:10:19.783992', NULL, '', false, '2026-04-23', 'EFECTIVO', 24600.00, '2026-05', 'EGRESO', NULL, 69, NULL, 1, NULL, 1),
	(84, '2026-05-04 16:12:44.412167', NULL, '', false, '2026-04-23', 'EFECTIVO', 1960.00, '2026-05', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(85, '2026-05-04 16:13:20.937886', NULL, '', false, '2026-04-24', 'EFECTIVO', 1900.00, '2026-05', 'EGRESO', NULL, 72, NULL, 1, NULL, 1),
	(86, '2026-05-04 16:13:57.41385', NULL, '', false, '2026-04-24', 'EFECTIVO', 3250.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(87, '2026-05-04 16:14:54.068265', NULL, 'Luz', false, '2026-04-24', 'EFECTIVO', 22104.00, '2026-05', 'EGRESO', NULL, 1, NULL, 1, NULL, 1),
	(88, '2026-05-04 16:16:36.827358', NULL, 'Cargador, cubrecamas, cable monitor, frazada y carro', false, '2026-04-24', 'EFECTIVO', 62948.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(89, '2026-05-04 16:17:09.503051', NULL, '', false, '2026-04-25', 'EFECTIVO', 5860.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(90, '2026-05-04 16:17:25.283153', NULL, '', false, '2026-04-25', 'EFECTIVO', 1490.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(91, '2026-05-04 16:18:01.949471', NULL, '', false, '2026-04-25', 'EFECTIVO', 16400.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(92, '2026-05-04 16:18:44.046237', NULL, 'Sushi', false, '2026-04-25', 'EFECTIVO', 36012.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(93, '2026-05-04 16:19:02.775399', NULL, '', false, '0004-05-24', 'EFECTIVO', 1180.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(94, '2026-05-04 16:19:22.310966', NULL, '', false, '2026-04-25', 'EFECTIVO', 5500.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(95, '2026-05-04 16:20:00.006229', NULL, '', false, '2026-04-26', 'EFECTIVO', 1430.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(97, '2026-05-04 16:24:10.223488', NULL, '', false, '2026-04-25', 'EFECTIVO', 1180.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(98, '2026-05-04 16:25:19.524888', NULL, '', false, '2026-04-26', 'EFECTIVO', 1900.00, '2026-05', 'EGRESO', NULL, 72, NULL, 1, NULL, 1),
	(99, '2026-05-04 16:25:37.268917', NULL, '', false, '2026-04-27', 'EFECTIVO', 5270.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(100, '2026-05-04 16:26:42.429', NULL, '', false, '2026-04-28', 'EFECTIVO', 1900.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(101, '2026-05-04 16:28:11.590726', NULL, '', false, '2026-04-29', 'EFECTIVO', 4299.00, '2026-05', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(102, '2026-05-04 16:28:42.085419', NULL, '', false, '2026-04-29', 'EFECTIVO', 1000.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(103, '2026-05-04 16:28:54.429842', NULL, '', false, '2026-04-29', 'EFECTIVO', 8330.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(104, '2026-05-04 16:29:10.413642', NULL, '', false, '2026-04-29', 'EFECTIVO', 1900.00, '2026-05', 'EGRESO', NULL, 72, NULL, 1, NULL, 1),
	(105, '2026-05-04 16:29:27.438281', NULL, '', false, '2026-04-29', 'EFECTIVO', 5400.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(106, '2026-05-04 16:30:09.122965', NULL, 'mcdonalds', false, '2026-04-30', 'EFECTIVO', 31897.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(107, '2026-05-04 16:31:14.657241', NULL, '', false, '2026-04-30', 'EFECTIVO', 7980.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(108, '2026-05-04 16:31:33.172566', NULL, '', false, '2026-04-30', 'EFECTIVO', 990.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(109, '2026-05-04 16:31:51.110386', NULL, '', false, '2026-05-01', 'EFECTIVO', 3000.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(110, '2026-05-04 16:32:40.972574', NULL, '', false, '2026-05-02', 'EFECTIVO', 4000.00, '2026-05', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(111, '2026-05-04 16:33:01.138142', NULL, '', false, '2026-05-02', 'EFECTIVO', 4895.00, '2026-05', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(112, '2026-05-04 16:34:01.186926', NULL, '', false, '2026-05-02', 'EFECTIVO', 18482.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(113, '2026-05-04 16:34:17.128294', NULL, '', false, '2026-05-03', 'EFECTIVO', 13300.00, '2026-05', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(114, '2026-05-04 17:47:06.507252', NULL, 'Wifi', false, '2026-04-24', 'EFECTIVO', 15514.00, '2026-05', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(115, '2026-05-04 17:47:38.613382', NULL, '', false, '2026-04-24', 'EFECTIVO', 10200.00, '2026-05', 'EGRESO', NULL, 1, NULL, 34, NULL, 1);
INSERT INTO public.transaccion VALUES
	(116, '2026-05-04 17:50:26.1304', NULL, 'Wifi mamá', false, '2026-04-24', 'EFECTIVO', 16334.00, '2026-05', 'EGRESO', NULL, 69, NULL, 34, NULL, 1),
	(117, '2026-05-04 17:50:46.807391', NULL, '', false, '2026-04-24', 'EFECTIVO', 14922.00, '2026-05', 'EGRESO', NULL, 69, NULL, 34, NULL, 1),
	(119, '2026-05-04 17:51:25.3582', NULL, '', false, '2026-04-26', 'EFECTIVO', 12800.00, '2026-05', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(120, '2026-05-04 17:51:41.557225', NULL, '', false, '2026-04-26', 'EFECTIVO', 2690.00, '2026-05', 'EGRESO', NULL, 74, NULL, 34, NULL, 1),
	(121, '2026-05-04 17:55:02.789918', NULL, '', false, '2026-04-28', 'EFECTIVO', 1709.00, '2026-05', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(123, '2026-05-04 17:56:07.288507', NULL, '', false, '2026-05-01', 'EFECTIVO', 700.00, '2026-05', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(124, '2026-05-04 17:56:25.186261', NULL, '', false, '2026-05-03', 'EFECTIVO', 4480.00, '2026-05', 'EGRESO', NULL, 2, NULL, 34, NULL, 1),
	(126, '2026-05-04 18:00:09.111726', NULL, '', false, '2026-04-30', 'EFECTIVO', 9159.00, '2026-04', 'EGRESO', NULL, 34, NULL, 35, NULL, 1),
	(127, '2026-05-04 18:01:00.647537', NULL, '', false, '2026-05-04', 'EFECTIVO', 24600.00, '2026-04', 'INGRESO', NULL, 76, NULL, 35, NULL, 1),
	(128, '2026-05-04 18:01:34.387362', NULL, 'Arriendo', true, '2026-05-04', 'EFECTIVO', 330966.00, '2026-04', 'EGRESO', NULL, 1, NULL, 35, NULL, 1),
	(129, '2026-05-04 18:02:03.113564', NULL, '', false, '2026-05-04', 'EFECTIVO', 653715.00, '2026-04', 'EGRESO', NULL, 34, NULL, 35, NULL, 1),
	(130, '2026-05-04 18:02:40.466681', NULL, '', false, '2026-05-04', 'EFECTIVO', 738668.00, '2026-04', 'EGRESO', NULL, 34, NULL, 35, NULL, 1),
	(131, '2026-05-04 18:06:25.699769', NULL, '', false, '2026-05-04', 'EFECTIVO', 105996.00, '2026-04', 'EGRESO', NULL, 34, NULL, 35, NULL, 1),
	(125, '2026-05-04 17:59:23.806264', NULL, '', true, '2026-04-30', 'TRANSFERENCIA', 1930000.00, '2026-04', 'INGRESO', NULL, 75, NULL, 35, NULL, 1),
	(132, '2026-05-04 18:06:47.015835', NULL, '', false, '2026-05-04', 'EFECTIVO', 39587.00, '2026-04', 'EGRESO', NULL, 34, NULL, 35, NULL, 1),
	(177, '2026-09-19 22:57:24.405527', NULL, '', false, '2026-07-30', 'EFECTIVO', 25900.00, '2026-08', 'EGRESO', NULL, 80, NULL, 1, NULL, 1),
	(179, '2026-09-19 22:57:50.085541', NULL, '', false, '2026-07-30', 'EFECTIVO', 12900.00, '2026-08', 'EGRESO', NULL, 80, NULL, 1, NULL, 1),
	(181, '2026-09-19 22:58:12.304003', NULL, '', false, '2026-07-30', 'EFECTIVO', 1900.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(183, '2026-09-19 22:58:47.038628', NULL, '', false, '2026-07-31', 'EFECTIVO', 1490.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(186, '2026-09-19 22:59:28.890951', NULL, '', false, '2026-08-01', 'EFECTIVO', 1630.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(1, '2026-09-19 15:37:45.187197', 5, 'Olla Depilatoria + cubre sillón', false, '2026-05-05', 'CREDITO', 4995.00, '2026-09', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(138, '2026-09-19 22:04:34.508044', NULL, '', false, '2026-08-19', 'CREDITO', 2318.00, '2026-09', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(136, '2026-09-19 22:04:14.870786', NULL, '', false, '2026-08-19', 'EFECTIVO', 2890.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(286, '2026-09-30 01:58:41.245831', NULL, 'Cineplanet Webpay', false, '2026-08-22', 'CREDITO', 13300.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(135, '2026-09-19 22:03:58.85906', NULL, '', false, '2026-08-19', 'EFECTIVO', 2727.00, '2026-09', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(4, '2026-09-19 21:56:32.096631', 3, '', false, '2026-06-23', 'CREDITO', 9998.00, '2026-09', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(133, '2026-09-19 22:01:23.086208', 3, '', false, '2026-06-27', 'CREDITO', 5563.00, '2026-09', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(2, '2026-09-19 15:40:44.347204', 4, 'Bolso + cubre sillon + reloj, + cobertor + manta', false, '2026-06-15', 'CREDITO', 13166.00, '2026-09', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(137, '2026-09-19 22:04:28.357097', NULL, '', false, '2026-08-19', 'EFECTIVO', 2000.00, '2026-09', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(139, '2026-09-19 22:09:21.308129', 4, '', false, '2026-05-06', 'CREDITO', 4995.00, '2026-08', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(140, '2026-09-19 22:09:38.998913', 3, '', false, '2026-05-22', 'CREDITO', 20848.00, '2026-08', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(280, '2026-09-30 01:58:41.245831', NULL, 'Tuu*entre Perros Y Gat', false, '2026-08-21', 'CREDITO', 2000.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(142, '2026-09-19 22:10:47.816297', 3, '', false, '2026-06-16', 'CREDITO', 8986.00, '2026-08', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(281, '2026-09-30 01:58:41.245831', NULL, 'Your Vet Veterinaria', false, '2026-08-21', 'CREDITO', 99000.00, '2026-09', 'EGRESO', NULL, 70, NULL, 1, NULL, 1),
	(144, '2026-09-19 22:12:18.596173', 2, '', false, '2026-06-24', 'CREDITO', 5562.00, '2026-08', 'EGRESO', 3, 2, NULL, 1, NULL, 1),
	(145, '2026-09-19 22:15:56.780127', NULL, '', false, '2026-07-19', 'EFECTIVO', 4000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(146, '2026-09-19 22:16:12.339918', NULL, '', false, '2026-07-19', 'EFECTIVO', 1570.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(152, '2026-09-19 22:45:32.015869', NULL, '', false, '2026-07-22', 'EFECTIVO', 4680.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(151, '2026-09-19 22:17:24.516116', NULL, '', false, '2026-07-21', 'EFECTIVO', 2400.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(153, '2026-09-19 22:46:50.036055', NULL, '', false, '2026-07-22', 'EFECTIVO', 1512.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(154, '2026-09-19 22:47:09.920431', NULL, '', false, '2026-07-22', 'EFECTIVO', 2390.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(155, '2026-09-19 22:49:23.631291', NULL, '', false, '2026-07-22', 'EFECTIVO', 12950.00, '2026-08', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(156, '2026-09-19 22:49:37.999106', NULL, '', false, '2026-07-22', 'EFECTIVO', 400.00, '2026-08', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(157, '2026-09-19 22:49:50.477047', NULL, '', false, '2026-07-22', 'EFECTIVO', 1000.00, '2026-08', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(158, '2026-09-19 22:50:18.346672', NULL, '', false, '2026-07-23', 'EFECTIVO', 3420.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(159, '2026-09-19 22:51:28.358203', NULL, '', false, '2026-07-24', 'EFECTIVO', 11150.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(160, '2026-09-19 22:51:44.031317', NULL, '', false, '2026-07-24', 'EFECTIVO', 6000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(161, '2026-09-19 22:51:54.462939', NULL, '', false, '2026-07-24', 'EFECTIVO', 1600.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(162, '2026-09-19 22:52:05.736009', NULL, '', false, '2026-07-24', 'EFECTIVO', 850.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(163, '2026-09-19 22:52:20.646401', NULL, '', false, '2026-07-24', 'EFECTIVO', 2445.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1);
INSERT INTO public.transaccion VALUES
	(164, '2026-09-19 22:53:29.81858', NULL, '', false, '2026-07-26', 'EFECTIVO', 10000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(165, '2026-09-19 22:53:43.480573', NULL, '', false, '2026-07-27', 'EFECTIVO', 1000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(166, '2026-09-19 22:54:01.839561', NULL, '', false, '2026-07-27', 'EFECTIVO', 4200.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(167, '2026-09-19 22:54:10.587883', NULL, '', false, '2026-07-27', 'EFECTIVO', 1710.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(168, '2026-09-19 22:54:24.336616', NULL, '', false, '2026-07-28', 'EFECTIVO', 420.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(169, '2026-09-19 22:54:36.801767', NULL, '', false, '2026-07-28', 'EFECTIVO', 2490.00, '2026-08', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(170, '2026-09-19 22:54:44.921028', NULL, '', false, '2026-07-28', 'EFECTIVO', 9545.00, '2026-08', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(171, '2026-09-19 22:55:00.911626', NULL, '', false, '2026-07-28', 'EFECTIVO', 4830.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(172, '2026-09-19 22:55:12.896208', NULL, '', false, '2026-07-28', 'EFECTIVO', 4180.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(173, '2026-09-19 22:55:32.912656', NULL, '', false, '2026-07-28', 'EFECTIVO', 1100.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(174, '2026-09-19 22:55:47.310919', NULL, '', false, '2026-07-29', 'EFECTIVO', 14000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(175, '2026-09-19 22:55:56.407356', NULL, '', false, '2026-07-29', 'EFECTIVO', 1710.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(188, '2026-09-19 22:59:54.271374', NULL, '', false, '2026-08-02', 'EFECTIVO', 1800.00, '2026-08', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(282, '2026-09-30 01:58:41.245831', NULL, 'Payscan*serv Multiven', false, '2026-08-22', 'CREDITO', 1700.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(143, '2026-09-19 22:11:58.473956', 2, '', false, '2026-06-23', 'CREDITO', 9998.00, '2026-08', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(147, '2026-09-19 22:16:27.137608', NULL, '', false, '2026-07-20', 'EFECTIVO', 2690.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(148, '2026-09-19 22:16:44.485862', NULL, '', false, '2026-07-20', 'EFECTIVO', 2690.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(150, '2026-09-19 22:17:08.241947', NULL, '', false, '2026-07-21', 'EFECTIVO', 1710.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(149, '2026-09-19 22:16:56.624647', NULL, '', false, '2026-07-21', 'EFECTIVO', 1900.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(283, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *mariecor', false, '2026-08-22', 'CREDITO', 3900.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(284, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *petshopl', false, '2026-08-22', 'CREDITO', 10500.00, '2026-09', 'EGRESO', NULL, 70, NULL, 1, NULL, 1),
	(285, '2026-09-30 01:58:41.245831', NULL, 'Mp *kfcappcl', false, '2026-08-22', 'CREDITO', 8050.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(288, '2026-09-30 01:58:41.245831', NULL, 'Tupan Mhv7yaqc', false, '2026-08-24', 'CREDITO', 2050.00, '2026-09', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(289, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *christia', false, '2026-08-24', 'CREDITO', 20798.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(292, '2026-09-30 01:58:41.245831', NULL, 'Maisi Spa', false, '2026-08-25', 'CREDITO', 1200.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(290, '2026-09-30 01:58:41.245831', NULL, 'Bac Panoteca', false, '2026-08-24', 'CREDITO', 132580.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(291, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *lacumbre', false, '2026-08-24', 'CREDITO', 9200.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(279, '2026-09-30 01:58:41.245831', NULL, 'Almacen Cristo De J', false, '2026-08-20', 'CREDITO', 1730.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(176, '2026-09-19 22:56:15.615239', NULL, '', false, '2026-07-30', 'EFECTIVO', 24803.00, '2026-08', 'EGRESO', NULL, 1, NULL, 1, NULL, 1),
	(178, '2026-09-19 22:57:39.142952', NULL, '', false, '2026-07-30', 'EFECTIVO', 7610.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(180, '2026-09-19 22:58:01.661476', NULL, '', false, '2026-07-30', 'EFECTIVO', 600.00, '2026-08', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(182, '2026-09-19 22:58:21.022931', NULL, '', false, '2026-07-30', 'EFECTIVO', 5360.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(184, '2026-09-19 22:58:57.460707', NULL, '', false, '2026-07-31', 'EFECTIVO', 1710.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(185, '2026-09-19 22:59:14.999313', NULL, '', false, '2026-08-01', 'EFECTIVO', 2690.00, '2026-08', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(187, '2026-09-19 22:59:43.078589', NULL, '', false, '2026-08-02', 'EFECTIVO', 2000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(189, '2026-09-19 23:00:05.224577', NULL, '', false, '2026-08-03', 'EFECTIVO', 1000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(293, '2026-09-30 01:58:41.245831', NULL, 'Okima Spa.', false, '2026-08-25', 'CREDITO', 18300.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(191, '2026-09-19 23:07:57.928677', NULL, '', false, '2026-08-03', 'EFECTIVO', 2000.00, '2026-08', 'EGRESO', NULL, 72, NULL, 1, NULL, 1),
	(192, '2026-09-19 23:08:05.706768', NULL, '', false, '2026-08-03', 'EFECTIVO', 2525.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(193, '2026-09-19 23:08:21.727372', NULL, '', false, '2026-08-04', 'EFECTIVO', 600.00, '2026-08', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(194, '2026-09-19 23:08:33.772671', NULL, '', false, '2026-08-05', 'EFECTIVO', 2500.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(195, '2026-09-19 23:08:46.420841', NULL, '', false, '2026-08-05', 'EFECTIVO', 5000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(197, '2026-09-19 23:09:05.483043', NULL, '', false, '2026-08-05', 'EFECTIVO', 1900.00, '2026-08', 'EGRESO', NULL, 72, NULL, 1, NULL, 1),
	(198, '2026-09-19 23:09:27.290831', NULL, '', false, '2026-08-06', 'EFECTIVO', 2630.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(199, '2026-09-19 23:09:44.791185', NULL, '', false, '2026-08-08', 'EFECTIVO', 16580.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(200, '2026-09-19 23:09:54.601774', NULL, '', false, '2026-08-08', 'EFECTIVO', 16638.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(201, '2026-09-19 23:10:19.692913', NULL, '', false, '2026-08-08', 'EFECTIVO', 3500.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(202, '2026-09-19 23:10:28.532523', NULL, '', false, '2026-08-08', 'EFECTIVO', 2300.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(203, '2026-09-19 23:10:39.582251', NULL, '', false, '2026-08-08', 'EFECTIVO', 5550.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(204, '2026-09-19 23:11:00.141721', NULL, '', false, '2026-08-14', 'EFECTIVO', 3900.00, '2026-08', 'EGRESO', NULL, 2, NULL, 1, NULL, 1);
INSERT INTO public.transaccion VALUES
	(205, '2026-09-19 23:11:30.848213', NULL, '', false, '2026-08-14', 'EFECTIVO', 1500.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(206, '2026-09-19 23:11:41.827809', NULL, '', false, '2026-08-16', 'EFECTIVO', 2380.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(207, '2026-09-19 23:11:53.764203', NULL, '', false, '2026-08-17', 'EFECTIVO', 2000.00, '2026-08', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(208, '2026-09-19 23:12:03.38187', NULL, '', false, '2026-08-17', 'EFECTIVO', 4400.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(209, '2026-09-19 23:12:12.181399', NULL, '', false, '2026-08-17', 'EFECTIVO', 5390.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(210, '2026-09-19 23:12:21.339339', NULL, '', false, '2026-08-17', 'EFECTIVO', 1230.00, '2026-08', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(211, '2026-09-19 23:12:33.215117', NULL, '', false, '2026-08-19', 'EFECTIVO', 4902.00, '2026-08', 'EGRESO', NULL, 34, NULL, 1, NULL, 1),
	(212, '2026-09-28 22:36:08.845402', 3, '', false, '2026-06-15', 'CREDITO', 13166.00, '2026-08', 'EGRESO', 6, 2, NULL, 1, NULL, 1),
	(196, '2026-09-19 23:08:56.248267', NULL, '', false, '2026-08-05', 'EFECTIVO', 3160.00, '2026-08', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(213, '2026-09-30 00:55:32.714997', 2, 'MP MERCADO LIBRE SANTIAGO CH', false, '2026-07-06', 'CREDITO', 24268.00, '2026-08', 'EGRESO', 6, 2, NULL, 34, NULL, 1),
	(216, '2026-09-30 00:55:32.714997', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-07-21', 'CREDITO', 1459.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(217, '2026-09-30 00:55:32.714997', NULL, 'MP YOURVET LAS CONDES CH', false, '2026-07-22', 'CREDITO', 99000.00, '2026-08', 'EGRESO', NULL, 70, NULL, 34, NULL, 1),
	(218, '2026-09-30 00:55:32.714997', NULL, 'ALMACENES VILUSA SANTIAGO CH', false, '2026-07-23', 'CREDITO', 1250.00, '2026-08', 'EGRESO', NULL, 78, NULL, 34, NULL, 1),
	(219, '2026-09-30 00:55:32.714997', NULL, 'PETCO CHILE SANTIAGO CH', false, '2026-07-23', 'CREDITO', 58742.00, '2026-08', 'EGRESO', NULL, 70, NULL, 34, NULL, 1),
	(220, '2026-09-30 00:55:32.714997', NULL, 'PAYU UBER EATS SANTIAGO CH', false, '2026-07-23', 'CREDITO', 20715.00, '2026-08', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(221, '2026-09-30 00:55:32.714997', NULL, 'TUU ENTRE PERROS Y GAT PUENTE ALTO CH', false, '2026-07-24', 'CREDITO', 2000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 34, NULL, 1),
	(222, '2026-09-30 00:55:32.714997', NULL, 'MP MCDONALDS LAS CONDES CH', false, '2026-07-25', 'CREDITO', 22573.00, '2026-08', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(223, '2026-09-30 00:55:32.714997', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-07-26', 'CREDITO', 3869.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(224, '2026-09-30 00:55:32.714997', NULL, 'MERCADOPAGO BIGMARKE LAS CONDES CH', false, '2026-07-26', 'CREDITO', 2300.00, '2026-08', 'EGRESO', NULL, 78, NULL, 34, NULL, 1),
	(225, '2026-09-30 00:55:32.714997', NULL, 'JOHNNY ROCKETS FLORI SANTIAGO CH', false, '2026-07-26', 'CREDITO', 26884.00, '2026-08', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(226, '2026-09-30 00:55:32.714997', NULL, 'MERCADOPAGO BIGMARKE LAS CONDES CH', false, '2026-07-26', 'CREDITO', 1800.00, '2026-08', 'EGRESO', NULL, 78, NULL, 34, NULL, 1),
	(227, '2026-09-30 00:55:32.714997', NULL, 'UBER LAS CONDES CH', false, '2026-07-26', 'CREDITO', 3990.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(228, '2026-09-30 00:55:32.714997', NULL, 'MP CINESEINVERSIONES LAS CONDES CH', false, '2026-07-26', 'CREDITO', 13000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 34, NULL, 1),
	(229, '2026-09-30 00:55:32.714997', NULL, 'DL GOOGLE YOUTUBE SANTIAGO', false, '2026-07-29', 'CREDITO', 5500.00, '2026-08', 'EGRESO', NULL, 74, NULL, 34, NULL, 1),
	(230, '2026-09-30 00:55:32.714997', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-07-30', 'CREDITO', 17614.00, '2026-08', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(231, '2026-09-30 00:55:32.714997', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-07-30', 'CREDITO', 12990.00, '2026-08', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(232, '2026-09-30 00:55:32.714997', 1, 'NEAT GASTO COMUN SAN FELIPE CH', false, '2026-07-30', 'CREDITO', 66208.00, '2026-08', 'EGRESO', 3, 1, NULL, 34, NULL, 1),
	(233, '2026-09-30 00:55:32.714997', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-07-30', 'CREDITO', 4640.00, '2026-08', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(234, '2026-09-30 00:55:32.714997', NULL, 'MP WHOOSHCLSPA LAS CONDES CH', false, '2026-07-31', 'CREDITO', 1337.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(235, '2026-09-30 00:55:32.714997', NULL, 'C. VERDE JM CARRE 6567 SANTIAGO CH', false, '2026-08-01', 'CREDITO', 6380.00, '2026-08', 'EGRESO', NULL, 80, NULL, 34, NULL, 1),
	(236, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-01', 'CREDITO', 2525.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(237, '2026-09-30 00:55:32.714997', NULL, 'TUU ENTRE PERROS Y GAT PUENTE ALTO CH', false, '2026-08-02', 'CREDITO', 2000.00, '2026-08', 'EGRESO', NULL, 78, NULL, 34, NULL, 1),
	(238, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-02', 'CREDITO', 1630.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(239, '2026-09-30 00:55:32.714997', NULL, 'TGR', false, '2026-08-03', 'CREDITO', 3600.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(240, '2026-09-30 00:55:32.714997', NULL, 'MP WHOOSHCLSPA LAS CONDES CH', false, '2026-08-06', 'CREDITO', 820.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(241, '2026-09-30 00:55:32.714997', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-08-06', 'CREDITO', 17555.00, '2026-08', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(294, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *christia', false, '2026-08-26', 'CREDITO', 20798.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(295, '2026-09-30 01:58:41.245831', NULL, 'Okm Metro Los Leones', false, '2026-08-26', 'CREDITO', 4480.00, '2026-09', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(242, '2026-09-30 00:55:32.714997', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-08-06', 'CREDITO', 16564.00, '2026-08', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(243, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-09', 'CREDITO', 895.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(244, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-10', 'CREDITO', 735.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(245, '2026-09-30 00:55:32.714997', NULL, 'COMERCIAL RIOS SPA CHACABUCO CH', false, '2026-08-12', 'CREDITO', 4450.00, '2026-08', 'EGRESO', NULL, 2, NULL, 34, NULL, 1),
	(246, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-13', 'CREDITO', 1550.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(247, '2026-09-30 00:55:32.714997', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-15', 'CREDITO', 1630.00, '2026-08', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(248, '2026-09-30 00:55:32.714997', NULL, 'DL RAPPI CHILE RAPPI LAS CONDES CH', false, '2026-08-15', 'CREDITO', 15010.00, '2026-08', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(249, '2026-09-30 00:55:32.714997', NULL, 'UBER LIME HELP.UBER.C AMSTERDAM NLD', false, '2026-07-30', 'CREDITO', 1996.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(250, '2026-09-30 00:55:32.714997', NULL, 'LIME 2 VIAJES UPYG SAN FRANCISCO USA', false, '2026-08-10', 'CREDITO', 2000.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(251, '2026-09-30 00:55:32.714997', NULL, 'LIME PRIME UPYG SAN FRANCISCO USA', false, '2026-08-15', 'CREDITO', 2500.00, '2026-08', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(252, '2026-09-30 00:55:32.714997', NULL, 'PRIMA SEGURO DESGRAVAMEN', false, '2026-08-20', 'CREDITO', 1246.00, '2026-08', 'EGRESO', NULL, 34, NULL, 34, NULL, 1),
	(253, '2026-09-30 00:55:32.714997', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,066%', false, '2026-07-31', 'CREDITO', 393.00, '2026-08', 'EGRESO', NULL, 34, NULL, 34, NULL, 1);
INSERT INTO public.transaccion VALUES
	(254, '2026-09-30 01:03:14.928753', NULL, 'DL*AGENDAPRO SANTIAGO', false, '2026-07-23', 'CREDITO', 28000.00, '2026-08', 'EGRESO', NULL, 2, NULL, 36, NULL, 1),
	(255, '2026-09-30 01:03:14.928753', 10, 'AVANCE EN CUOTAS TE TASA INT. 3,15%', false, '2025-09-26', 'CREDITO', 68636.00, '2026-08', 'EGRESO', 12, 35, NULL, 36, NULL, 1),
	(256, '2026-09-30 01:03:14.928753', NULL, 'COMISION ADMINISTRACION MENSUAL', false, '2026-08-19', 'CREDITO', 10214.00, '2026-08', 'EGRESO', NULL, 34, NULL, 36, NULL, 1),
	(257, '2026-09-30 01:17:54.094814', 1, 'LOCAL 4434', false, '2026-08-12', 'CREDITO', 4099.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(258, '2026-09-30 01:17:54.094814', 1, 'MONARCH 1088 PROVIDENCI', false, '2026-07-08', 'CREDITO', 4663.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(259, '2026-09-30 01:17:54.094814', 1, 'LYON', false, '2026-07-08', 'CREDITO', 5325.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(260, '2026-09-30 01:17:54.094814', 2, 'MERPAGO*MERCADOLIBRE', false, '2026-07-03', 'CREDITO', 6333.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(261, '2026-09-30 01:17:54.094814', 1, 'PANDEMIA TATTOO 14', false, '2026-06-24', 'CREDITO', 5000.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(262, '2026-09-30 01:17:54.094814', 3, 'MP *MERCADO LIBRE', false, '2026-06-02', 'CREDITO', 37579.00, '2026-08', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(263, '2026-09-30 01:17:54.094814', 2, 'SB 734', false, '2026-06-01', 'CREDITO', 7100.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(264, '2026-09-30 01:17:54.094814', 3, 'PUNTO TICKET SPA WB', false, '2026-05-15', 'CREDITO', 38334.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(265, '2026-09-30 01:17:54.094814', 5, 'ENTEL VENTAS DE EQUIPOS', false, '2026-03-02', 'CREDITO', 31666.00, '2026-08', 'EGRESO', 18, 2, NULL, 37, NULL, 1),
	(266, '2026-09-30 01:17:54.094814', 8, 'MERCADOPAGO*MERCADO', false, '2025-12-29', 'CREDITO', 90692.00, '2026-08', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(267, '2026-09-30 01:17:54.094814', 10, 'MP *EMMA', false, '2025-11-03', 'CREDITO', 23992.00, '2026-08', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(268, '2026-09-30 01:17:54.094814', NULL, 'COMISION ADMINISTRACION MENSUAL', false, '2026-08-21', 'CREDITO', 5394.00, '2026-08', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(269, '2026-09-30 01:17:54.094814', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,264 %', false, '2026-08-12', 'CREDITO', 76.00, '2026-08', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(270, '2026-09-30 01:17:54.094814', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,800 %', false, '2026-08-04', 'CREDITO', 17879.00, '2026-08', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(271, '2026-09-30 01:17:54.094814', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,264 %', false, '2026-08-04', 'CREDITO', 79.00, '2026-08', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(272, '2026-09-30 01:17:54.094814', NULL, 'BANCHILE SEGUROS (REC) SANTIAGO', false, '2026-07-24', 'CREDITO', 12552.00, '2026-08', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(273, '2026-09-30 01:17:54.094814', 0, 'BODEGA ACUENTA PANOTECA', false, '2026-08-11', 'CREDITO', 9573.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(274, '2026-09-30 01:17:54.094814', 0, 'COMERCIAL PUELCHE LTDA', false, '2026-08-03', 'CREDITO', 9960.00, '2026-08', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(275, '2026-09-30 01:17:54.094814', 0, 'UNIV DIEGO PORTALES WES', false, '2026-08-03', 'CREDITO', 230033.00, '2026-08', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(276, '2026-09-29 22:19:47', NULL, 'Pago Arriendo', true, '2026-08-31', 'TRANSFERENCIA', 340056.00, '2026-08', 'EGRESO', NULL, 1, NULL, 35, NULL, 1),
	(277, '2026-09-29 22:22:04', NULL, 'Sueldo', true, '2026-08-31', 'TRANSFERENCIA', 1921926.00, '2026-08', 'INGRESO', NULL, 75, NULL, 35, NULL, 1),
	(278, '2026-09-29 22:22:04', NULL, 'Sueldo', true, '2026-09-30', 'TRANSFERENCIA', 1921926.00, '2026-09', 'INGRESO', NULL, 75, NULL, 35, NULL, 1),
	(298, '2026-09-30 01:58:41.245831', NULL, 'Movired Wb', false, '2026-08-27', 'CREDITO', 2000.00, '2026-09', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(299, '2026-09-30 01:58:41.245831', NULL, 'Tupan Mhv7yaqc', false, '2026-08-27', 'CREDITO', 2550.00, '2026-09', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(300, '2026-09-30 01:58:41.245831', NULL, 'Petco Chile', false, '2026-08-28', 'CREDITO', 28285.00, '2026-09', 'EGRESO', NULL, 70, NULL, 1, NULL, 1),
	(302, '2026-09-30 01:58:41.245831', NULL, 'Pago En Mercadopago 5', false, '2026-08-29', 'CREDITO', 1575.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(303, '2026-09-30 01:58:41.245831', NULL, 'Comercial Zheng Ltda', false, '2026-08-29', 'CREDITO', 14520.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(305, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *comercia', false, '2026-08-29', 'CREDITO', 14390.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(307, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *yogenfru', false, '2026-08-29', 'CREDITO', 6990.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(308, '2026-09-30 01:58:41.245831', NULL, 'Puro Kimchi Spa', false, '2026-08-30', 'CREDITO', 5000.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(309, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *servicio', false, '2026-08-30', 'CREDITO', 10500.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(310, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *fuhua', false, '2026-08-30', 'CREDITO', 13900.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(311, '2026-09-30 01:58:41.245831', NULL, 'Tuu*fantasia Cartoon', false, '2026-08-30', 'CREDITO', 5000.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(312, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *bigmarke', false, '2026-08-30', 'CREDITO', 3370.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(313, '2026-09-30 01:58:41.245831', NULL, 'Servicios Y Comercial', false, '2026-08-31', 'CREDITO', 4780.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(315, '2026-09-30 01:58:41.245831', NULL, 'Rafael Pizarro', false, '2026-09-01', 'CREDITO', 500.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(316, '2026-09-30 01:58:41.245831', NULL, 'Tupan Mhv7yaqc', false, '2026-09-01', 'CREDITO', 2000.00, '2026-09', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(304, '2026-09-30 01:58:41.245831', NULL, 'Wifi', true, '2026-08-29', 'CREDITO', 15702.00, '2026-09', 'EGRESO', NULL, 1, NULL, 1, NULL, 1),
	(306, '2026-09-30 01:58:41.245831', NULL, 'Luz', true, '2026-08-29', 'CREDITO', 24792.00, '2026-09', 'EGRESO', NULL, 1, NULL, 1, NULL, 1),
	(314, '2026-09-30 01:58:41.245831', NULL, 'Google One', true, '2026-09-01', 'CREDITO', 2690.00, '2026-09', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(296, '2026-09-30 01:58:41.245831', NULL, 'Cineplanet Webpay', false, '2026-08-26', 'CREDITO', 6600.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(297, '2026-09-30 01:58:41.245831', NULL, 'YT Premium', true, '2026-08-27', 'EFECTIVO', 11000.00, '2026-09', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(317, '2026-09-30 01:58:41.245831', NULL, 'Tuu*entre Perros Y Gat', false, '2026-09-03', 'CREDITO', 4500.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(318, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *mcdonald', false, '2026-09-04', 'CREDITO', 18513.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(319, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *botijr', false, '2026-09-04', 'CREDITO', 9000.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(320, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *mariecor', false, '2026-09-05', 'CREDITO', 1800.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(321, '2026-09-30 01:58:41.245831', NULL, 'Payu Uber Trip', false, '2026-09-05', 'CREDITO', 3035.00, '2026-09', 'EGRESO', NULL, 77, NULL, 1, NULL, 1);
INSERT INTO public.transaccion VALUES
	(323, '2026-09-30 01:58:41.245831', NULL, 'Ramen Ryoma Los Leones', false, '2026-09-05', 'CREDITO', 32340.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(324, '2026-09-30 01:58:41.245831', NULL, 'Movired Wb', false, '2026-09-05', 'CREDITO', 2000.00, '2026-09', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(326, '2026-09-30 01:58:41.245831', NULL, 'Uber', false, '2026-09-06', 'CREDITO', 5331.00, '2026-09', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(328, '2026-09-30 01:58:41.245831', NULL, 'Tupan Mhv7yaqc', false, '2026-09-07', 'CREDITO', 1690.00, '2026-09', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(329, '2026-09-30 01:58:41.245831', NULL, 'Comercial New Ideal', false, '2026-09-08', 'CREDITO', 4915.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(330, '2026-09-30 01:58:41.245831', NULL, 'Fasa Loc 393', false, '2026-09-08', 'CREDITO', 6374.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(331, '2026-09-30 01:58:41.245831', NULL, '23765-bk Suecia', false, '2026-09-09', 'CREDITO', 3900.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(332, '2026-09-30 01:58:41.245831', NULL, 'Okm Metro Los Leones', false, '2026-09-09', 'CREDITO', 2190.00, '2026-09', 'EGRESO', NULL, 68, NULL, 1, NULL, 1),
	(334, '2026-09-30 01:58:41.245831', NULL, 'Tupan Mhv7yaqc', false, '2026-09-11', 'CREDITO', 1840.00, '2026-09', 'EGRESO', NULL, 71, NULL, 1, NULL, 1),
	(336, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *bigmarke', false, '2026-09-12', 'CREDITO', 1950.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(340, '2026-09-30 01:58:41.245831', NULL, 'Merpago*mariecoromoto', false, '2026-09-14', 'CREDITO', 2000.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(342, '2026-09-30 01:58:41.245831', NULL, 'Merpago*papajohns', false, '2026-09-15', 'CREDITO', 19470.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(345, '2026-09-30 01:58:41.245831', NULL, 'Gran Avenida', false, '2026-09-15', 'CREDITO', 6233.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(347, '2026-09-30 01:58:41.245831', NULL, 'Alcantara 2', false, '2026-09-16', 'CREDITO', 3180.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(348, '2026-09-30 01:58:41.245831', NULL, 'Merpago*mariecoromoto', false, '2026-09-16', 'CREDITO', 1500.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(349, '2026-09-30 01:58:41.245831', NULL, 'Servicios Y Comercial', false, '2026-09-16', 'CREDITO', 5670.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(350, '2026-09-30 01:58:41.245831', NULL, 'Merpago*bigmarketspa', false, '2026-09-17', 'CREDITO', 1500.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(351, '2026-09-30 01:58:41.245831', NULL, 'Mp *fuhua', false, '2026-09-17', 'CREDITO', 13900.00, '2026-09', 'EGRESO', NULL, 67, NULL, 1, NULL, 1),
	(352, '2026-09-30 01:58:41.245831', NULL, 'Merpago*botijr', false, '2026-09-18', 'CREDITO', 6500.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(353, '2026-09-30 01:58:41.245831', NULL, 'Google *goog', false, '2026-08-22', 'CREDITO', 1790.00, '2026-09', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(354, '2026-09-30 01:58:41.245831', NULL, 'Lime*costo D', false, '2026-09-11', 'CREDITO', 600.00, '2026-09', 'EGRESO', NULL, 77, NULL, 1, NULL, 1),
	(356, '2026-09-30 01:58:41.245831', NULL, 'Servicio Administracion', false, '2026-09-19', 'CREDITO', 4916.00, '2026-09', 'EGRESO', NULL, 34, NULL, 1, NULL, 1),
	(394, '2026-09-30 01:59:09.099486', NULL, 'COMERCIAL NEW IDEAL GROSANTIAGO', false, '2026-08-25', 'CREDITO', 17130.00, '2026-09', 'EGRESO', NULL, 2, NULL, 37, NULL, 1),
	(395, '2026-09-30 01:59:09.099486', 11, 'MP *EMMA', false, '2025-11-03', 'CREDITO', 23992.00, '2026-09', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(396, '2026-09-30 01:59:09.099486', 9, 'MERCADOPAGO*MERCADO', false, '2025-12-29', 'CREDITO', 90692.00, '2026-09', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(397, '2026-09-30 01:59:09.099486', 6, 'ENTEL VENTAS DE EQUIPOS', false, '2026-03-02', 'CREDITO', 31666.00, '2026-09', 'EGRESO', 18, 2, NULL, 37, NULL, 1),
	(398, '2026-09-30 01:59:09.099486', 3, 'SB 734', false, '2026-06-01', 'CREDITO', 7099.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(399, '2026-09-30 01:59:09.099486', 4, 'MP *MERCADO LIBRE', false, '2026-06-02', 'CREDITO', 37579.00, '2026-09', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(400, '2026-09-30 01:59:09.099486', 2, 'PANDEMIA TATTOO 14', false, '2026-06-24', 'CREDITO', 5000.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(401, '2026-09-30 01:59:09.099486', 3, 'MERPAGO*MERCADOLIBRE', false, '2026-07-03', 'CREDITO', 6334.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(402, '2026-09-30 01:59:09.099486', 2, 'LYON', false, '2026-07-08', 'CREDITO', 5325.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(403, '2026-09-30 01:59:09.099486', 2, 'MONARCH 1088 PROVIDENCI', false, '2026-07-08', 'CREDITO', 4663.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(404, '2026-09-30 01:59:09.099486', 1, 'COMERCIAL PUELCHE LTDA', false, '2026-08-03', 'CREDITO', 9960.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(405, '2026-09-30 01:59:09.099486', 1, 'UNIV DIEGO PORTALES WES', false, '2026-08-03', 'CREDITO', 230033.00, '2026-09', 'EGRESO', 12, 2, NULL, 37, NULL, 1),
	(406, '2026-09-30 01:59:09.099486', 1, 'BODEGA ACUENTA PANOTECA', false, '2026-08-11', 'CREDITO', 9573.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(407, '2026-09-30 01:59:09.099486', 2, 'LOCAL 4434', false, '2026-08-12', 'CREDITO', 4099.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(408, '2026-09-30 01:59:09.099486', NULL, 'BANCHILE SEGUROS (REC) SANTIAGO', false, '2026-08-24', 'CREDITO', 12558.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(409, '2026-09-30 01:59:09.099486', NULL, 'COMISION ADMINISTRACION MENSUAL', false, '2026-09-22', 'CREDITO', 5411.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(410, '2026-09-30 01:59:09.099486', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,800 %', false, '2026-08-25', 'CREDITO', 2720.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(411, '2026-09-30 01:59:09.099486', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,264 %', false, '2026-08-31', 'CREDITO', 106.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(412, '2026-09-30 01:59:09.099486', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,264 %', false, '2026-09-01', 'CREDITO', 34.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(413, '2026-09-30 01:59:09.099486', NULL, 'IMPUESTO DECRETO LEY 3475 TASA 0,264 %', false, '2026-09-09', 'CREDITO', 61.00, '2026-09', 'EGRESO', NULL, 34, NULL, 37, NULL, 1),
	(339, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *lacumbre', false, '2026-09-12', 'CREDITO', 3600.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(355, '2026-09-30 01:58:41.245831', NULL, 'Lime*prime U', true, '2026-09-15', 'CREDITO', 2500.00, '2026-09', 'EGRESO', NULL, 74, NULL, 1, NULL, 1),
	(337, '2026-09-30 01:58:41.245831', NULL, 'Mercadopago *lacumbre', false, '2026-09-12', 'CREDITO', 8810.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(344, '2026-09-30 01:58:41.245831', NULL, 'Hip Lider Gran Avenida', false, '2026-09-15', 'CREDITO', 2650.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(343, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-15', 'CREDITO', 1250.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(327, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-06', 'CREDITO', 5000.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(325, '2026-09-30 01:58:41.245831', NULL, 'Tuu*pehuen', false, '2026-09-05', 'CREDITO', 2700.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(414, '2026-09-30 01:59:09.099486', 0, 'TRAVEL TIENDA TCOMP', false, '2026-08-24', 'CREDITO', 14166.00, '2026-09', 'EGRESO', 24, 2, NULL, 37, NULL, 1);
INSERT INTO public.transaccion VALUES
	(415, '2026-09-30 01:59:09.099486', 0, 'MERCADOPAGO*CENTROVISUA', false, '2026-08-29', 'CREDITO', 13333.00, '2026-09', 'EGRESO', 3, 80, NULL, 37, NULL, 1),
	(416, '2026-09-30 01:59:09.099486', 0, 'TRICOT GRAN AVENIDA', false, '2026-08-31', 'CREDITO', 4317.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(417, '2026-09-30 01:59:09.099486', 0, 'HIP LIDER GRAN AVENIDA', false, '2026-09-08', 'CREDITO', 7663.00, '2026-09', 'EGRESO', 3, 2, NULL, 37, NULL, 1),
	(357, '2026-09-30 01:59:46.368104', 3, 'MP MERCADO LIBRE SANTIAGO CH', false, '2026-07-06', 'CREDITO', 24268.00, '2026-09', 'EGRESO', 6, 2, NULL, 34, NULL, 1),
	(360, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-20', 'CREDITO', 3548.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(361, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-21', 'CREDITO', 3639.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(365, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-22', 'CREDITO', 5314.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(366, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-22', 'CREDITO', 3574.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(367, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-23', 'CREDITO', 3419.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(368, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-23', 'CREDITO', 3574.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(369, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-23', 'CREDITO', 7795.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(371, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-27', 'CREDITO', 6586.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(372, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-27', 'CREDITO', 3610.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(376, '2026-09-30 01:59:46.368104', NULL, 'MERCADO PAGO 5 TCOM SANTIAGO CH', false, '2026-08-29', 'CREDITO', 1567.00, '2026-09', 'EGRESO', NULL, 2, NULL, 34, NULL, 1),
	(378, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-30', 'CREDITO', 3620.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(379, '2026-09-30 01:59:46.368104', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-08-30', 'CREDITO', 815.00, '2026-09', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(380, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-08-30', 'CREDITO', 3079.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(381, '2026-09-30 01:59:46.368104', NULL, 'MERCADOPAGO MELT LAS CONDES CH', false, '2026-09-05', 'CREDITO', 18080.00, '2026-09', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(382, '2026-09-30 01:59:46.368104', NULL, 'RAPYD UBER EATS SANTIAGO CH', false, '2026-09-06', 'CREDITO', 11876.00, '2026-09', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(383, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER EATS SANTIAGO CH', false, '2026-09-10', 'CREDITO', 19866.00, '2026-09', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(384, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-09-12', 'CREDITO', 1991.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(385, '2026-09-30 01:59:46.368104', NULL, 'RED MOVILIDAD SANTIAGO SANTIAGO CH', false, '2026-09-13', 'CREDITO', 815.00, '2026-09', 'EGRESO', NULL, 68, NULL, 34, NULL, 1),
	(386, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER TRIP SANTIAGO CH', false, '2026-09-13', 'CREDITO', 5898.00, '2026-09', 'EGRESO', NULL, 77, NULL, 34, NULL, 1),
	(387, '2026-09-30 01:59:46.368104', NULL, 'PAYU UBER EATS SANTIAGO CH', false, '2026-09-19', 'CREDITO', 22125.00, '2026-09', 'EGRESO', NULL, 67, NULL, 34, NULL, 1),
	(388, '2026-09-30 01:59:46.368104', NULL, 'WL STEAM PURCHASE BELLEVUE USA - USD 4.97', false, '2026-09-09', 'CREDITO', 4600.00, '2026-09', 'EGRESO', NULL, 81, NULL, 34, NULL, 1),
	(389, '2026-09-30 01:59:46.368104', NULL, 'PRIMA SEGURO DESGRAVAMEN', false, '2026-09-21', 'CREDITO', 1025.00, '2026-09', 'EGRESO', NULL, 34, NULL, 34, NULL, 1),
	(390, '2026-09-30 02:00:01.802244', NULL, 'TRAVEL DUTY TCOMP SANTIAGO', false, '2026-08-26', 'CREDITO', 10220.00, '2026-09', 'EGRESO', NULL, 2, NULL, 36, NULL, 1),
	(391, '2026-09-30 02:00:01.802244', 11, 'AVANCE EN CUOTAS TE', false, '2025-09-26', 'CREDITO', 68636.00, '2026-09', 'EGRESO', 12, 35, NULL, 36, NULL, 1),
	(392, '2026-09-30 02:00:01.802244', 1, 'NORMALIZA S.A.', false, '2026-07-02', 'CREDITO', 136667.00, '2026-09', 'EGRESO', 3, 35, NULL, 36, NULL, 1),
	(393, '2026-09-30 02:00:01.802244', NULL, 'COMISION ADMINISTRACION MENSUAL', false, '2026-09-17', 'CREDITO', 10238.00, '2026-09', 'EGRESO', NULL, 34, NULL, 36, NULL, 1),
	(418, '2026-09-29 22:19:47', NULL, 'Pago Arriendo', true, '2026-09-30', 'TRANSFERENCIA', 340056.00, '2026-09', 'EGRESO', NULL, 1, NULL, 35, NULL, 1),
	(301, '2026-09-30 01:58:41.245831', NULL, 'Agua', true, '2026-08-29', 'CREDITO', 6180.00, '2026-09', 'EGRESO', NULL, 1, NULL, 1, NULL, 1),
	(359, '2026-09-30 01:59:46.368104', 2, 'Pago Gasto comun junio', false, '2026-07-30', 'CREDITO', 66206.00, '2026-09', 'EGRESO', 3, 35, NULL, 34, NULL, 1),
	(134, '2026-09-19 22:01:48.173625', NULL, '', false, '2026-08-19', 'CREDITO', 1490.00, '2026-09', 'EGRESO', NULL, 2, NULL, 1, NULL, 1),
	(373, '2026-09-30 01:59:46.368104', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', true, '2026-08-29', 'CREDITO', 12990.00, '2026-09', 'EGRESO', NULL, 82, NULL, 34, NULL, 1),
	(374, '2026-09-30 01:59:46.368104', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-08-29', 'CREDITO', 18018.00, '2026-09', 'EGRESO', NULL, 82, NULL, 34, NULL, 1),
	(375, '2026-09-30 01:59:46.368104', NULL, 'NEAT CUENTAS BASICAS SAN FELIPE CH', false, '2026-08-29', 'CREDITO', 16796.00, '2026-09', 'EGRESO', NULL, 82, NULL, 34, NULL, 1),
	(377, '2026-09-30 01:59:46.368104', NULL, 'NEAT GASTO COMUN SAN FELIPE CH', true, '2026-08-29', 'CREDITO', 100356.00, '2026-09', 'EGRESO', NULL, 1, NULL, 34, NULL, 1),
	(214, '2026-09-30 00:55:32.714997', 2, 'MAYORISTA 10 G. AVENID SANTIAGO CH', false, '2026-07-15', 'CREDITO', 12523.00, '2026-08', 'EGRESO', 3, 79, NULL, 34, NULL, 1),
	(96, '2026-05-04 16:21:26.093802', NULL, '', false, '2026-04-26', 'EFECTIVO', 119150.00, '2026-05', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(358, '2026-09-30 01:59:46.368104', 3, 'MAYORISTA 10 G. AVENID SANTIAGO CH', false, '2026-07-15', 'CREDITO', 12523.00, '2026-09', 'EGRESO', 3, 79, NULL, 34, NULL, 1),
	(341, '2026-09-30 01:58:41.245831', NULL, 'Ekono Lo Ovalle', false, '2026-09-15', 'CREDITO', 8570.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(215, '2026-09-30 00:55:32.714997', NULL, 'MAYORISTA 10 G. AVENID SANTIAGO CH', false, '2026-07-21', 'CREDITO', 102684.00, '2026-08', 'EGRESO', NULL, 79, NULL, 34, NULL, 1),
	(322, '2026-09-30 01:58:41.245831', NULL, 'Mayorista 10 G. Avenida', false, '2026-09-05', 'CREDITO', 51090.00, '2026-09', 'EGRESO', NULL, 79, NULL, 1, NULL, 1),
	(362, '2026-09-30 01:59:46.368104', NULL, 'DL GOOGLE YOUTUBE SANTIAGO', false, '2026-08-21', 'CREDITO', 3093.00, '2026-09', 'EGRESO', NULL, 81, NULL, 34, NULL, 1),
	(118, '2026-05-04 17:51:10.171495', NULL, '', true, '2026-04-25', 'CREDITO', 3990.00, '2026-05', 'EGRESO', NULL, 74, NULL, 34, NULL, 1),
	(364, '2026-09-30 01:59:46.368104', NULL, 'DL GOOGLE YOUTUBE SANTIAGO', false, '2026-08-21', 'CREDITO', 4400.00, '2026-09', 'EGRESO', NULL, 81, NULL, 34, NULL, 1),
	(363, '2026-09-30 01:59:46.368104', NULL, 'DL GOOGLE YOUTUBE SANTIAGO', false, '2026-08-21', 'CREDITO', 2200.00, '2026-09', 'EGRESO', NULL, 81, NULL, 34, NULL, 1),
	(370, '2026-09-30 01:59:46.368104', NULL, 'UBER LAS CONDES CH', true, '2026-08-26', 'CREDITO', 3990.00, '2026-09', 'EGRESO', NULL, 74, NULL, 34, NULL, 1),
	(335, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-11', 'CREDITO', 4500.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1);
INSERT INTO public.transaccion VALUES
	(346, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-15', 'CREDITO', 3750.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(333, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-10', 'CREDITO', 1000.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(338, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-09-12', 'CREDITO', 1750.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(287, '2026-09-30 01:58:41.245831', NULL, 'Almacenes Vilusa', false, '2026-08-23', 'CREDITO', 4260.00, '2026-09', 'EGRESO', NULL, 78, NULL, 1, NULL, 1),
	(122, '2026-05-04 17:55:45.510493', NULL, '', true, '2026-04-29', 'CREDITO', 5500.00, '2026-05', 'EGRESO', NULL, 74, NULL, 34, NULL, 1);


--
-- Data for Name: transaccion_tag; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: app_user_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.app_user_id_seq', 1, false);


--
-- Name: categoria_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.categoria_id_seq', 81, true);


--
-- Name: comercio_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.comercio_id_seq', 1, false);


--
-- Name: cuenta_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.cuenta_id_seq', 37, true);


--
-- Name: tag_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.tag_id_seq', 1, false);


--
-- Name: transaccion_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.transaccion_id_seq', 419, true);


--
-- Name: app_user app_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);


--
-- Name: categoria categoria_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id);


--
-- Name: comercio comercio_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comercio
    ADD CONSTRAINT comercio_pkey PRIMARY KEY (id);


--
-- Name: cuenta cuenta_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta
    ADD CONSTRAINT cuenta_pkey PRIMARY KEY (id);


--
-- Name: tag tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag
    ADD CONSTRAINT tag_pkey PRIMARY KEY (id);


--
-- Name: transaccion transaccion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT transaccion_pkey PRIMARY KEY (id);


--
-- Name: transaccion_tag transaccion_tag_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_tag
    ADD CONSTRAINT transaccion_tag_pkey PRIMARY KEY (transaccion_id, tag_id);


--
-- Name: ix_app_user_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_app_user_email ON public.app_user USING btree (lower((email)::text));


--
-- Name: ix_categoria_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_categoria_user_id ON public.categoria USING btree (user_id);


--
-- Name: ix_comecio_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_comecio_user_id ON public.comercio USING btree (user_id);


--
-- Name: ix_cuenta_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_cuenta_user_id ON public.cuenta USING btree (user_id);


--
-- Name: ix_tag_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tag_user_id ON public.tag USING btree (user_id);


--
-- Name: ix_transaccion_categoria_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaccion_categoria_id ON public.transaccion USING btree (categoria_id);


--
-- Name: ix_transaccion_cuenta_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaccion_cuenta_id ON public.transaccion USING btree (cuenta_id);


--
-- Name: ix_transaccion_subcategoria_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaccion_subcategoria_id ON public.transaccion USING btree (subcategoria_id);


--
-- Name: ix_transaccion_user_fecha; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaccion_user_fecha ON public.transaccion USING btree (user_id, fecha);


--
-- Name: ix_transaccion_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaccion_user_id ON public.transaccion USING btree (user_id);


--
-- Name: ux_app_user_auth0_sub; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_app_user_auth0_sub ON public.app_user USING btree (auth0_sub);


--
-- Name: transaccion fk49gt4ksw72134a816skt5uq5j; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT fk49gt4ksw72134a816skt5uq5j FOREIGN KEY (subcategoria_id) REFERENCES public.categoria(id);


--
-- Name: tag fk5qq6vtihb9pe0b7a9qp9wsixg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tag
    ADD CONSTRAINT fk5qq6vtihb9pe0b7a9qp9wsixg FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: transaccion_tag fk5ytceekjuoria67st94t183nv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_tag
    ADD CONSTRAINT fk5ytceekjuoria67st94t183nv FOREIGN KEY (transaccion_id) REFERENCES public.transaccion(id);


--
-- Name: transaccion_tag fk9gxhp531rvgc435pfkw1ts4ot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion_tag
    ADD CONSTRAINT fk9gxhp531rvgc435pfkw1ts4ot FOREIGN KEY (tag_id) REFERENCES public.tag(id);


--
-- Name: transaccion fkao4575hqvwp5hk2dfvko5rj46; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT fkao4575hqvwp5hk2dfvko5rj46 FOREIGN KEY (comercio_id) REFERENCES public.comercio(id);


--
-- Name: comercio fkbp7ge2e3sybumxfqkchboewke; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.comercio
    ADD CONSTRAINT fkbp7ge2e3sybumxfqkchboewke FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: categoria fkcwgfv36dkgmefgqu46dehjyqx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT fkcwgfv36dkgmefgqu46dehjyqx FOREIGN KEY (parent_id) REFERENCES public.categoria(id) DEFERRABLE INITIALLY DEFERRED;


--
-- Name: categoria fke15da92dvjbpygwwgpyqgybhq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT fke15da92dvjbpygwwgpyqgybhq FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: transaccion fkfjwxirsflc8sqx1np84xk5xgs; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT fkfjwxirsflc8sqx1np84xk5xgs FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- Name: transaccion fkk7db1p3y2mxyhrflylujs3bx7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT fkk7db1p3y2mxyhrflylujs3bx7 FOREIGN KEY (categoria_id) REFERENCES public.categoria(id);


--
-- Name: transaccion fkkkale73n3p5vwbgxa49yiyqgx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaccion
    ADD CONSTRAINT fkkkale73n3p5vwbgxa49yiyqgx FOREIGN KEY (cuenta_id) REFERENCES public.cuenta(id);


--
-- Name: cuenta fks4jwbl4mt87p9u6gimf5r6kut; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cuenta
    ADD CONSTRAINT fks4jwbl4mt87p9u6gimf5r6kut FOREIGN KEY (user_id) REFERENCES public.app_user(id);


--
-- PostgreSQL database dump complete
--

\unrestrict vViWXysj2QglTbYZetHQ2u8RG8ksQNRk6f6MEwUL0qFPnqvAllCEp3sw0d4SKfq


CREATE TABLE dglusers(
       id SERIAL PRIMARY KEY,
       username TEXT UNIQUE NOT NULL,
       email TEXT UNIQUE NOT NULL,
       env TEXT,
       password TEXT NOT NULL,
       flags INT NOT NULL DEFAULT 0
);

CREATE TABLE xlogfile(
       id SERIAL PRIMARY KEY,
       version TEXT,
       points BIGINT,
       deathdnum BIGINT,
       deathdname TEXT,
       deathlev BIGINT,
       maxlvl BIGINT,
       dlev_name TEXT,
       hp BIGINT,
       maxhp BIGINT,
       deaths BIGINT,
       deathdate DATE,
       birthdate DATE,
       uid BIGINT,
       "role" TEXT,
       race TEXT,
       gender TEXT,
       align TEXT,
       "name" TEXT,
       death TEXT,
       death_uniq TEXT,
       conduct TEXT,
       turns BIGINT,
       achieve TEXT,
       realtime BIGINT,
       starttime TIMESTAMP,
       endtime TIMESTAMP,
       gender0 TEXT,
       align0 TEXT,
       flags TEXT,
       region TEXT,
       event TEXT,
       carried TEXT,
       elbereths BIGINT,
       xplevel BIGINT,
       exp BIGINT,
       mode TEXT,
       gold BIGINT,
       fpos BIGINT
);

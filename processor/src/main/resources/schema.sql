-- someone creates a batch
CREATE TABLE sfdc_batch (
  _id          INT(11) AUTO_INCREMENT PRIMARY KEY,
  batch_id     VARCHAR(255) NOT NULL,
  api_endpoint TEXT         NOT NULL,
#   query        TEXT         NOT NULL, # not sure if we need this yet.
  access_token TEXT         NOT NULL);

-- dump all the leads from SFDC and associate them with a record in this table.
-- this way, there is only one copy of a given lead, and it can be enriched across multiple requests.
CREATE TABLE sfdc_batch_lead (
  _id      INT(11) AUTO_INCREMENT PRIMARY KEY,
  batch_id VARCHAR(255) NOT NULL REFERENCES sfdc_batch (batch_id),
  lead_id  INT(11)      NOT NULL REFERENCES sfdc_lead (_id),
  UNIQUE (batch_id, lead_id));

/*
CREATE TABLE sfdc_contact (
  _id                 INT(11) AUTO_INCREMENT PRIMARY KEY,
  batch_id            VARCHAR(255) NOT NULL,
  latitude            DOUBLE,
  longitude           DOUBLE,
  email               VARCHAR(255),
  mailing_state       VARCHAR(255),
  mailing_country     VARCHAR(255),
  mailing_street      VARCHAR(255),
  mailing_city        VARCHAR(255),
  mailing_postal_code VARCHAR(255),
  sfdc_id             VARCHAR(255) UNIQUE,
  first_name          VARCHAR(255),
  last_name           VARCHAR(255));
*/

CREATE TABLE sfdc_lead (
  _id                      INT(11) AUTO_INCREMENT PRIMARY KEY,
  sfdc_id                  VARCHAR(255) UNIQUE,
  latitude                 DOUBLE,
  longitude                DOUBLE,
  annual_revenue           TEXT,
  city                     TEXT,
  company                  TEXT,
  converted_account_id     TEXT,
  converted_contact_id     TEXT,
  converted_date           DATETIME,
  converted_opportunity_id TEXT,
  country                  TEXT,
  created_by_id            TEXT,
  created_date             DATETIME,
  description              TEXT,
  email                    TEXT,
  email_bounced_date       DATETIME,
  email_bounced_reason     TEXT,
  fax                      TEXT,
  first_name               TEXT,
  id                       TEXT,
  industry                 TEXT,
  is_converted             TEXT,
  is_deleted               TEXT,
  is_unread_by_owner       TEXT,
  jigsaw                   TEXT,
  jigsaw_contact_id        TEXT,
  last_activity_date       DATETIME,
  last_modified_by_id      TEXT,
  last_modified_date       DATETIME,
  last_name                TEXT,
  lead_source              TEXT,
  master_record_id         TEXT,
  mobile_phone             TEXT,
  number_of_employees      TEXT,
  owner_id                 TEXT,
  phone                    TEXT,
  postal_code              TEXT,
  rating                   TEXT,
  salutation               TEXT,
  state                    TEXT,
  status                   TEXT,
  street                   TEXT,
  system_modstamp          TEXT,
  title                    TEXT,
  website                  TEXT);

CREATE VIEW sfdc_directory AS SELECT
                                street      AS street,
                                email       AS email,
                                city        AS city,
                                state       AS state,
                                postal_code AS postal_code,
                                latitude    AS latitude,
                                longitude   AS longitude,
                                _id         AS sfdc_id,
                                batch_id    AS batch_id,
                                'lead'      AS record_type
                              FROM sfdc_lead
                              UNION
                              SELECT
                                mailing_street      AS street,
                                email               AS email,
                                mailing_city        AS city,
                                mailing_state       AS state,
                                mailing_postal_code AS postal_code,
                                latitude            AS latitude,
                                longitude           AS longitude,
                                _id                 AS sfdc_id,
                                batch_id            AS batch_id,
                                'contact'           AS record_type
                              FROM sfdc_contact
                              GROUP BY email;
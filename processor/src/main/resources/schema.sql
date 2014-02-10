
create table sfdc_batch(  batch_id text not null, api_endpoint text not null, access_token text not null);

create table sfdc_contact(
    _id int(11) auto_increment primary key ,
    batch_id varchar(255) not null ,
    latitude double,
    longitude double,
    email varchar (255),
    mailing_state varchar (255),
    mailing_country varchar (255),
    mailing_street varchar (255),
    mailing_city varchar (255),
    mailing_postal_code varchar (255),
    sfdc_id varchar (255) UNIQUE,
    first_name varchar (255),
    last_name varchar (255));

create table sfdc_lead(
  batch_id varchar(255) not null ,   _id int(11) auto_increment primary key  ,  sfdc_id varchar (255) UNIQUE,  latitude double, longitude double,  annual_revenue text,city text,company text,converted_account_id text,converted_contact_id text,converted_date datetime,converted_opportunity_id text,country text,created_by_id text,created_date datetime,description text,email text,email_bounced_date datetime,
  email_bounced_reason text,fax text,first_name text,id text,industry text,is_converted text,is_deleted text,is_unread_by_owner text,jigsaw text,jigsaw_contact_id text,
  last_activity_date datetime,last_modified_by_id text,last_modified_date datetime,last_name text,lead_source text,master_record_id text,
  mobile_phone text,number_of_employees text,owner_id text,phone text,postal_code text,rating text,salutation text,state text,status text,street text,system_modstamp text,title text,website text ) ;


create view sfdc_directory as select  street as street,  email as email, city as city, state as state ,  postal_code as postal_code,  latitude as latitude, longitude as longitude, _id as sfdc_id, batch_id as batch_id  ,
    'lead' as record_type  from sfdc_lead  union select   mailing_street as street ,email as email, mailing_city as city, mailing_state as state, mailing_postal_code as postal_code, latitude as latitude, longitude as longitude, _id as sfdc_id, batch_id as batch_id  ,
     'contact' as record_type from sfdc_contact group by email ;

create table sfdc_batch( batch_id text not null, api_endpoint text not null, access_token text not null);

create table sfdc_contact( mailing_state varchar (255), mailing_country varchar (255), mailing_street varchar (255), mailing_postal_code varchar (255), email varchar (255), sfdc_id varchar (255) UNIQUE, first_name varchar (255), last_name varchar (255));
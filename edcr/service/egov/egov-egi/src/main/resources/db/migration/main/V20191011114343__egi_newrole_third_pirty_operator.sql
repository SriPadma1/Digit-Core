insert into eg_role (id,name,description,internal,createddate,createdby,lastmodifieddate,lastmodifiedby) 
values (nextval('seq_eg_role'), 'Third Party Operator', 'Third party access', false, now(), 1, now(), 1);
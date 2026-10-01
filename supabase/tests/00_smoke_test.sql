begin;
select plan(1);
select ok(true, 'pgTAP est opérationnel');
select * from finish();
rollback;

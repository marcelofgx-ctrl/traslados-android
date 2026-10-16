DO $qa$
DECLARE
  v_request uuid:=extensions.gen_random_uuid();
  v_created jsonb; v_again jsonb; v_read jsonb;
  v_id uuid; v_rolled_back boolean:=false;
BEGIN
  BEGIN
    v_created:=public.customer_request_pickup_v1(
      p_request_id=>v_request,p_mode=>'now',
      p_origin_text=>'QA De Madrid',p_origin_lat=>-34.8274694,p_origin_lng=>-55.969099,
      p_destination_text=>'QA Carrasco Terminal',p_destination_lat=>-34.83696,p_destination_lng=>-56.01638,
      p_passengers=>1,p_comments=>'QA TRANSACCIONAL: NO PERSISTIR',
      p_customer_name=>'QA TRANSACCIONAL',p_customer_phone=>'999999999000')::jsonb;
    v_id:=(v_created->>'id')::uuid;
    ASSERT v_created->>'status'='PENDIENTE' AND length(v_created->>'code')>0;
    v_again:=public.customer_request_pickup_v1(
      p_request_id=>v_request,p_mode=>'now',
      p_origin_text=>'QA De Madrid',p_origin_lat=>-34.8274694,p_origin_lng=>-55.969099,
      p_destination_text=>'QA Carrasco Terminal',p_destination_lat=>-34.83696,p_destination_lng=>-56.01638,
      p_passengers=>1,p_comments=>'QA TRANSACCIONAL: NO PERSISTIR',
      p_customer_name=>'QA TRANSACCIONAL',p_customer_phone=>'999999999000')::jsonb;
    ASSERT (v_again->>'id')::uuid=v_id AND (v_again->>'reused')::boolean;
    v_read:=public.get_reservation_by_token_v4(v_created->>'public_token')::jsonb;
    ASSERT v_read IS NOT NULL AND v_read::text LIKE '%PENDIENTE%';
    RAISE SQLSTATE 'ZX001' USING MESSAGE='QA rollback always';
  EXCEPTION WHEN SQLSTATE 'ZX001' THEN v_rolled_back:=true;
  END;
  ASSERT v_rolled_back;
  ASSERT NOT EXISTS(SELECT 1 FROM public.reservations WHERE id=v_id OR client_request_id=v_request);
  ASSERT NOT EXISTS(SELECT 1 FROM public.reservation_events WHERE reservation_id=v_id);
END
$qa$;
SELECT 'PASS: request receipt, idempotent retry, token tracking; reservation and events rolled back' AS qa_result;

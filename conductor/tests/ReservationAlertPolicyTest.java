package uy.com.traslados.conductor;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Standalone JVM regression; never contacts a live passenger or modifies database. */
public class ReservationAlertPolicyTest {
  static void check(boolean value,String message){
    if(!value)throw new AssertionError(message);
  }
  public static void main(String[] args){
    long now=Instant.parse("2026-10-17T16:31:00Z").toEpochMilli();
    String recent=Instant.ofEpochMilli(now-2*60000L).toString();
    String coldStart=OffsetDateTime.ofInstant(Instant.ofEpochMilli(now-60000L),ZoneOffset.UTC).toString();
    String expired=Instant.ofEpochMilli(now-91*60000L).toString();
    String older=Instant.ofEpochMilli(now-6*3600000L).toString();
    check(ReservationAlertPolicy.recentPending("PENDIENTE",recent,now),"New pending visible on first poll");
    check(ReservationAlertPolicy.recentPending("PENDIENTE",coldStart,now),"Offset timestamps accepted");
    check(!ReservationAlertPolicy.recentPending("PENDIENTE",expired,now),"Do not notify expired immediate request");
    check(!ReservationAlertPolicy.recentPending("PENDIENTE",older,now),"Do not spam old pending requests");
    check(!ReservationAlertPolicy.recentPending("ACEPTADA",recent,now),"No notification for accepted ride");
    check(!ReservationAlertPolicy.recentPending("FINALIZADA",recent,now),"No terminal notifications");
    check(!ReservationAlertPolicy.recentPending("PENDIENTE","garbage",now),"Invalid date fails closed");
    check(ReservationAlertPolicy.undelivered(0,now),"First delivery allowed");
    check(!ReservationAlertPolicy.undelivered(now-60000L,now),"No duplicate after restart");
    check(ReservationAlertPolicy.undelivered(now-25L*3600000L,now),"Delivery TTL");
    System.out.println("PASS: cold start, recent pending, expired request, no duplicate notifications");
  }
}

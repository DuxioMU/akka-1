/**
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */
package jdocs.tutorial_3;

import java.util.stream.Stream;
import java.util.stream.Collectors;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scalatest.junit.JUnitSuite;

import java.util.Optional;

public class DeviceGroupTest extends JUnitSuite {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create();
  }

  @AfterClass
  public static void teardown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  //#device-group-test-registration
  @Test
  public void testRegisterDeviceActor() {
    TestKit probe = new TestKit(system);
    ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor1 = probe.getLastSender();

    groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
    probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
    ActorRef deviceActor2 = probe.getLastSender();
    Assert.assertNotEquals(deviceActor1, deviceActor2);

    // Check that the device actors are workingl
    deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
    Assert.assertEquals((Long) 0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
    deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
    Assert.assertEquals((Long) 1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
  }

    @Test
    public void testIgnoreRequestsForWrongGroupId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("wrongGroup", "device1"), probe.getRef());
        probe.expectNoMsg();
    }
    //#device-group-test-registration

    //#device-group-test3
    @Test
    public void testReturnSameActorForSameDeviceId() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.getLastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.getLastSender();
        Assert.assertEquals(deviceActor1, deviceActor2);
    }
    //#device-group-test3

    //#device-group-list-terminate-test
    @Test
    public void testListActiveDevices() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
        DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
        Assert.assertEquals((Long) 0L, reply.requestId);
        Assert.assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);
    }

    @Test
    public void teestListActiveDevicesAfterOneShutsDown() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef toShutDown = probe.getLastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);

        groupActor.tell(new DeviceGroup.RequestDeviceList(0L), probe.getRef());
        DeviceGroup.ReplyDeviceList reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
        Assert.assertEquals((Long) 0L, reply.requestId);
        Assert.assertEquals(Stream.of("device1", "device2").collect(Collectors.toSet()), reply.ids);

        probe.watch(toShutDown);
        toShutDown.tell(PoisonPill.getInstance(), ActorRef.noSender());
        probe.expectTerminated(toShutDown);

        groupActor.tell(new DeviceGroup.RequestDeviceList(1L), probe.getRef());
        reply = probe.expectMsgClass(DeviceGroup.ReplyDeviceList.class);
        Assert.assertEquals((Long) 1L, reply.requestId);
        Assert.assertEquals(Stream.of("device2").collect(Collectors.toSet()), reply.ids);
    }
    //#device-group-list-terminate-test
}
package myau.event.impl;

import myau.event.Event;

public class PostRaytraceEvent implements Event {
  public final float partialTicks;

  public PostRaytraceEvent(float partialTicks) {
    this.partialTicks = partialTicks;
  }
}

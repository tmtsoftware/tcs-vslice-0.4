package tcs.pk.wrapper;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.annotation.*;

@Platform(
    includepath = {"/usr/local/include/"},
    linkpath = {"/usr/local/lib/"},
    include = {"tpk-jni/interface.h"},
    link = {"tpk-jni"}

)
public class TpkC extends Pointer {
  static {
    try {
      Loader.load();
    } catch(Exception e) {
      e.printStackTrace();
    }
  }

  public TpkC() {
    allocate();
  }

  private native void allocate();

  // to call the getter and setter functions
  public native void init();

  public native void newDemands(double mAz, double mEl, double eAz, double eEl, double m3R, double m3T);

  public native void newTarget(double ra, double dec);

  public native void offset(double raO, double decO);
}

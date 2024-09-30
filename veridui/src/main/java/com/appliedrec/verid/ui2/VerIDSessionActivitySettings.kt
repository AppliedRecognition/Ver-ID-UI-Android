package com.appliedrec.verid.ui2

import android.os.Parcel
import android.os.Parcelable
import com.appliedrec.verid.core2.VerID
import com.appliedrec.verid.core2.session.LivenessDetectionSessionSettings
import com.appliedrec.verid.core2.session.VerIDSessionSettings

class VerIDSessionActivitySettings

@JvmOverloads
constructor(
    verID: VerID,
    val sessionSettings: VerIDSessionSettings? = LivenessDetectionSessionSettings(),
    val shouldSessionRecordVideo: Boolean = false,
    val shouldSessionSpeakPrompts: Boolean = false,
    val cameraLocation: CameraLocation = CameraLocation.FRONT
) : Parcelable {

    val verIDInstanceId: Int

    init {
        verIDInstanceId = verID.instanceId
    }

    constructor(parcel: Parcel) : this(
        VerID.getInstance(parcel.readInt()),
        parcel.readParcelable(VerIDSessionSettings::class.java.classLoader),
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.readByte().toInt().let { CameraLocation.values()[it] }
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(verIDInstanceId)
        parcel.writeParcelable(sessionSettings, flags)
        parcel.writeByte(if (shouldSessionRecordVideo) 1 else 0)
        parcel.writeByte(if (shouldSessionSpeakPrompts) 1 else 0)
        parcel.writeByte(cameraLocation.ordinal.toByte())
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<VerIDSessionActivitySettings> {
        override fun createFromParcel(parcel: Parcel): VerIDSessionActivitySettings {
            return VerIDSessionActivitySettings(parcel)
        }

        override fun newArray(size: Int): Array<VerIDSessionActivitySettings?> {
            return arrayOfNulls(size)
        }
    }

}

package com.jonahbauer.qed.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;

@Data
public class Event implements Comparable<Event>, Parcelable {
    private final long id;
    private String title;
    private Date start;
    private Date end;
    private Date deadline;
    private String startString;
    private String endString;
    private String deadlineString;
    private int cost;
    private int maxParticipants;
    private String hotel;
    private String hotelAddress;
    private String emailOrga;
    private String emailAll;
    private final Map<String, Registration> participants = new HashMap<>();

    @Override
    @NonNull
    public String toString() {
        List<String> entries = new LinkedList<>();
        if (title != null) entries.add( "\"title\":\"" + title + "\"");
        if (startString != null) entries.add( "\"start\":\"" + startString + "\"");
        if (endString != null) entries.add( "\"end\":\"" + endString + "\"");
        if (deadlineString != null) entries.add( "\"deadline\":\"" + deadlineString + "\"");
        if (cost != -1) entries.add( "\"cost\":" + cost);
        if (maxParticipants != -1) entries.add( "\"maxMember\":" + maxParticipants);
        if (id != -1) entries.add( "\"id\":" + id);
        if (hotel != null) entries.add( "\"hotel\":\"" + hotel + "\"");
        if (hotelAddress != null) entries.add( "\"hotelAddress\":\"" + hotelAddress + "\"");
        if (emailOrga != null) entries.add( "\"emailOrga\":\"" + emailOrga + "\"");
        if (emailAll != null) entries.add( "\"emailAll\":\"" + emailAll + "\"");
        if (!participants.isEmpty()) entries.add( "\"participants\":\"" + participants + "\"");
        return entries.stream().collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public int compareTo(@NonNull Event o) {
        if (this.start != null && o.start != null) {
            return this.start.compareTo(o.start);
        } else {
            return this.startString.compareTo(o.startString);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeString(startString);
        dest.writeSerializable(start);
        dest.writeString(endString);
        dest.writeSerializable(end);
        dest.writeString(deadlineString);
        dest.writeSerializable(deadline);
        dest.writeInt(cost);
        dest.writeInt(maxParticipants);
        dest.writeString(hotel);
        dest.writeInt(participants.size());
        for (Map.Entry<String, Registration> entry : participants.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeLong(entry.getValue().getId());
        }
    }

    public static final Parcelable.Creator<Event> CREATOR = new Parcelable.Creator<Event>() {
        @Override
        public Event createFromParcel(@NonNull Parcel source) {
            Event event = new Event(source.readLong());
            event.title = source.readString();
            event.startString = source.readString();
            event.start = (Date) source.readSerializable();
            event.endString = source.readString();
            event.end = (Date) source.readSerializable();
            event.deadlineString = source.readString();
            event.deadline = (Date) source.readSerializable();
            event.cost = source.readInt();
            event.maxParticipants = source.readInt();
            event.hotel = source.readString();

            int participantCount = source.readInt();
            for (int i = 0; i < participantCount; i++) {
                event.participants.put(
                        source.readString(),
                        new Registration(source.readLong())
                );
            }
            return event;
        }

        @Override
        public Event[] newArray(int size) {
            return new Event[size];
        }
    };
}

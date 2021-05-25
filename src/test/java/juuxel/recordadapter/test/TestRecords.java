/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package juuxel.recordadapter.test;

import blue.endless.jankson.Comment;
import blue.endless.jankson.annotation.SerializedName;

public final class TestRecords {
    public record Basket(Vegetable vegetable, int count, @Comment("the total mass of this basket") Double mass) {}
    public record Vegetable(String name, String colour, @SerializedName("long name") String longName) {}
    record Inaccessible(String x) {}
    public record DuplicateSerialisedNames(@SerializedName("discoteque") String songName, String discoteque) {}
}

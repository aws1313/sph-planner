package de.koenidv.sph.parsing

import de.koenidv.sph.R
import de.koenidv.sph.database.CoursesDb
import de.koenidv.sph.objects.Course
import java.text.SimpleDateFormat
import java.util.*

const val TYPE_UNKNOWN = -1 // Unknown id type
const val TYPE_INTERNAL = 0 // Example: m_bar_1
const val TYPE_GMB = 1 // Example: M-GK-3
const val TYPE_SPH = 2 // Example: Q3Mbar01 - GYM
const val TYPE_NAMED = 3 // Example: Mathematik GK 13
const val TYPE_NUMBER = 4 // Example: 760

//  Created by koenidv on 06.12.2020.
class IdParser {

    /**
     * Returns an internal id for a gmb id and teacher id
     * Trying to get an existing internal id for a course which is not in the db or list will mostly fail
     * @param courseGmbId External course id from gmb
     * @param teacherId The course's teacher id
     * @param allCourses List of all courses to compare to. Will use database if unspecified
     * @return Internal id for this course
     */
    fun getCourseIdWithGmb(courseGmbId: String, teacherId: String, forceNewId: Boolean = false, allCourses: List<Course>? = null): String {
        val courseDb = CoursesDb.getInstance()

        if (!forceNewId) {
            val existingCourseId = CoursesDb.getInstance().getCourseIdByGmbId(courseGmbId.toLowerCase(Locale.ROOT))
            if (existingCourseId != null) return existingCourseId
        }

        // Warning: trying to get an existing internal id for a course which is not in the db will mostly fail

        // GMB id might in some cases not include a dash
        val classType: String = if (courseGmbId.contains("-"))
            courseGmbId.substring(0, courseGmbId.indexOf("-")).take(8).toLowerCase(Locale.ROOT)
        else
            courseGmbId.take(8).toLowerCase(Locale.ROOT)

        // Make everything lowercase
        val teachId = teacherId.toLowerCase(Locale.ROOT)

        // Check if a course with the same internal id but different data already exists
        var index = 1
        var coursesWithSameId: List<Course>
        // Get courses with same subject from dataset or database
        coursesWithSameId = allCourses?.filter { it.courseId.startsWith(classType + "_") }
                ?: CoursesDb.getInstance().getByInternalPrefix(classType + "_")
        coursesWithSameId = coursesWithSameId.filter { it.isLK == courseGmbId.toLowerCase(Locale.ROOT).contains("lk") }
        var checkForNewIndex = coursesWithSameId.isNotEmpty()
        var courseToCheck: Course
        while (checkForNewIndex) {
            // Use current index if no same course was found
            if (index == coursesWithSameId.size + 1) {
                checkForNewIndex = false
            } else {
                // Use current index if there's already an interal id for this specific course
                courseToCheck = coursesWithSameId[index - 1]
                if (Utility().nullOrEquals(courseToCheck.gmb_id, courseGmbId.toLowerCase(Locale.ROOT))
                        // No real need to check this as check for gmb_id already includes it, we'll keep it here anyways
                        && Utility().nullOrEquals(courseToCheck.isLK, courseGmbId.toLowerCase(Locale.ROOT).contains("lk")))
                    checkForNewIndex = false
                else
                // If internal id belongs to another course, try the next one
                    index++
            }
        }

        if (forceNewId) {
            // Make sure there isn't another course with the same id
            // This might happen if a teacher has both a GK and LK with the same subject
            while ((allCourses?.filter { it.courseId == classType + "_" + teachId + "_" + index }
                            ?: courseDb.getByInternalPrefix(classType + "_" + teachId + "_" + index)).isNotEmpty()) {
                index++
            }
        }
        // Return id, example: m_bar_1 or ch_cas_2
        return classType + "_" + teachId + "_" + index
    }

    /**
     * Get an existing internal id for a gmb id, without a teacher
     */
    fun getCourseIdWithGmb(courseGmbId: String): String? = CoursesDb.getInstance().getCourseIdByGmbId(courseGmbId.toLowerCase(Locale.ROOT))

    /**
     * Returns an internal id for a gmb id and teacher id
     * @param courseSphId External course id from sph
     * @param teacherId The course's teacher id
     * @param isLK If course is LK. Strongly advised to be specified for better accuracy
     * @param allCourses List of all courses to compare to. Will use database if unspecified
     * @return Internal id for this course
     */
    fun getCourseIdWithSph(courseSphId: String, teacherId: String, isLK: Boolean?, allCourses: List<Course>? = null): String {
        val courseDb = CoursesDb.getInstance()

        // Extract some useful information from the external course id
        val values = Regex("""([A-Z]{1,8})(?:[a-zäöü]+)?(\d{2,3})""").find(courseSphId.replace("-", ""))!!.groupValues
        val classType = values[1].toLowerCase(Locale.ROOT) // Get class type (i.e. G from Q3Gvac03)
        val sphIndex = values[2].toInt().toString() // Get sph's index (i.e. 3 from Q3Gvac03)

        // Check if there's already a matching course using sph's index
        // ! This is still very vague
        var filteredCourses = allCourses?.filter { it.courseId == classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" + sphIndex }
                ?: listOf(courseDb.getByInternalId(classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" + sphIndex))
        // The list should only contain 0 or 1 elements (unique id)
        // Apart from classType and teacherId, isLK is the only property we can trust
        if (filteredCourses.isNotEmpty()) {
            return if (isLK != null && filteredCourses[0].isLK == isLK) {
                classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" + sphIndex
            } else {
                // Just assume it's correct if nothing is specified for isLK
                classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" + sphIndex
            }
        }

        // sph index is not the same as internal index or course has not been seen yet
        // Check if there's a matching course with any index
        filteredCourses = allCourses?.filter { it.courseId == classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" }
                ?: courseDb.getByInternalPrefix(classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_")
        if (isLK != null) filteredCourses = filteredCourses.filter { it.isLK == isLK }

        // If there are multiple courses with the same subject by the same teacher which are all LK/GK,
        // there's no way for us to know which one is meant
        // Therefore we'll just return the first one and hope for the best
        // todo Ask user if course assignment is too vague
        if (filteredCourses.isNotEmpty()) return filteredCourses[0].courseId

        // If a matching course hasn't been seen before, we'll create a new id
        // Check if there are any courses with the same prefix and use the next index
        filteredCourses = allCourses?.filter { it.courseId == classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" }
                ?: courseDb.getByInternalPrefix(classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_")
        return classType + "_" + teacherId.toLowerCase(Locale.ROOT) + "_" + filteredCourses.size + 1
    }

    fun getCourseIdPrefixWithNamedId(namedId: String, teacherId: String): String? {
        val prefixMap = Utility().parseStringArray(R.array.namedid_course_prefixes)
        return if (prefixMap.containsKey(namedId.substring(0, namedId.indexOf(" "))))
            (prefixMap[namedId.substring(0, namedId.indexOf(" "))] +
                    "_" + teacherId).toLowerCase(Locale.ROOT)
        else null
    }

    /**
     * Estimates the type of an course id
     * @param courseId Id to check
     * @return An estimate of the id's type
     */
    fun getCourseIdType(courseId: String): Int {
        val patternNumber = "^\\d{1,4}\$".toRegex() // Number ID: 1-4 digits only
        val patternNamed = "^.*[^-0-9]\\s+.*\$".toRegex() // Named ID: Containing whitespace not following on digit or hyphen
        val patternSph = "^.*\\d\\s-\\s.*\$".toRegex() // SPH ID: Ending with at least one digit followed by " - " and non-digits
        val patternGmb = "^\\w{1,8}-\\w{1,2}-\\d{1,2}\$".toRegex() // GMB ID: 3 hyphen-separated sets: 1-6 chars, 1-2 chars, 1-2 digits
        val patternInternal = "^\\w{1,8}_\\w{1,4}_\\d{1,2}\$".toRegex() // Internal ID: 2 underscore-separated sets of 1-6 word characters and 1-2 digits in the end

        return when {
            patternNumber.matches(courseId) -> TYPE_NUMBER
            patternNamed.matches(courseId) -> TYPE_NAMED
            patternSph.matches(courseId) -> TYPE_SPH
            patternGmb.matches(courseId) -> TYPE_GMB
            patternInternal.matches(courseId) -> TYPE_INTERNAL
            else -> TYPE_UNKNOWN // Id does not match any type
        }
    }

    /**
     * Get a not before used attachment id
     */
    fun getFileAttachmentId(courseId: String, date: Date, allIds: List<String>): String {
        val internalDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        var fileIndex = 0
        var fileId: String
        do {
            fileIndex++ // first index 1
            fileId = courseId + "_attach-" + internalDateFormat.format(date) + "_" + fileIndex
        } while (allIds.contains(fileId))
        return fileId
    }

    /**
     * Get a new id for a link attachment
     */
    fun getLinkAttachmentId(courseId: String, date: Date, allIds: List<String>): String {
        val internalDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        var fileIndex = 0
        var linkId: String
        do {
            fileIndex++ // first index 1
            linkId = courseId + "_link-" + internalDateFormat.format(date) + "_" + fileIndex
        } while (allIds.contains(linkId))
        return linkId
    }
}
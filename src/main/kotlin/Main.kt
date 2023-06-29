@file:Suppress("SameParameterValue")

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

data class Message(
    var senderDiscordTag: String,
    var senderServerTag: String,
    var content: String,
    var timestamp: String,
    var channel: String
)

data class User(
    val discordTag: String,
    val serverTag: String,
    var firstMessage: Message,
    var messagesTotal: Int,
    var wordOccurrences: Map<String, Int>,
    var allMessages: String,
    var uniqueWordsCount: Int
)


fun main() {
    val executionTime = measureTimeMillis {

        val pathToResources = "src/main/resources/"
        val pathToMyTwitchgeneral = pathToResources + "mytwitchgeneral.json"
        val pathToMyGeneral = pathToResources + "mygeneral.json"

        val f1Exist = File(pathToMyTwitchgeneral).exists()
        println("mytwitchgeneral.json exist - $f1Exist")
        if (!f1Exist) {
            val twitchgeneralMessages =
                parseJson("C:\\Users\\aDragon\\IdeaProjects\\doSmtWithMsgs\\src\\main\\resources\\twitchgeneral.json")
            writeJson(twitchgeneralMessages, pathToMyTwitchgeneral)
        }

        val f2Exist = File(pathToMyGeneral).exists()
        println("mygeneral.json exist - $f2Exist")
        if (!f2Exist) {
            val generalMessages =
                parseJson("C:\\Users\\aDragon\\IdeaProjects\\doSmtWithMsgs\\src\\main\\resources\\general.json")
            writeJson(generalMessages, pathToMyGeneral)
        }

        val messages = parseMyJson(pathToMyTwitchgeneral) as MutableList
        messages.addAll(parseMyJson(pathToMyGeneral))
        println("\nBoth messages parsed")

        val users = countMessages(messages)
        println("\nMessages counted")

        writeResultJson(users, pathToResources + "result.json", 30)
    }
    val formattedExTime = String.format("%.2f", executionTime / 90000.0)
    println("Время выполнения программы: $formattedExTime мин")
}


private fun countMessages(messages: List<Message>): List<User> {
    println("\nStart counting")
    fun getOccurrences(text: String): Pair<Map<String, Int>, Int> {
        val map = mutableMapOf<String, Int>()

        val words = text.split(" ")
        words.forEach { word ->
            map[word] = map.getOrDefault(word, 0) + 1
        }
        return map to words.size
    }

    fun editContent(text: String): String {
        val t = text.replace(Regex("[^a-zа-я ]"), "")
        val excludedWords = setOf(
            "тоже", "только", "чтобы", "чтоб", "вообще", "меня", "даже", "нечем", "который", "которая", "просто",
            "того", "может", "чтото", "изза", "этот", "либо"
        )
        var t1 = ""
        t.split(" ")
            .filter { (it.trim().isNotBlank() && it.trim() !in excludedWords) && it.length > 3 }
            .forEach {
                t1 += "${it.trim()} "
            }

        return t1.trim()
    }

    val users = mutableListOf<User>()
    try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        var earliestDate = LocalDateTime.parse("3333-11-11 11:11:11", formatter)

        /* Set of users server tag's */
        val usersServerTagsSet: Set<String> = messages.map { it.senderServerTag }.toSet()

        /* List of users sorted by name in case of increase collect of all messages */
        val sortedByName = messages.sortedBy { it.senderServerTag }

        /* for iteration  */
        var currentUser: User? = null

        println("\nMsgs count - ${sortedByName.size}")
        sortedByName.forEach { msg ->
            if (msg.content == "Joined the server.") return@forEach

            val editedContent =
                usersServerTagsSet.fold(msg.content) { acc, serverTag -> acc.replace("@$serverTag", "") }.lowercase()
            val curContent = editContent(editedContent)

            if (currentUser == null || currentUser!!.serverTag != msg.senderServerTag) {

                currentUser =
                    User(msg.senderDiscordTag, msg.senderServerTag, msg, 1, mutableMapOf(), curContent + "\n", 0)
                users.add(currentUser!!)

            } else {
                currentUser!!.messagesTotal++
                currentUser!!.allMessages += curContent + "\n"

                /* If current msg timestamp < current earliest, then change current earliest  */
                val curMsgDate = LocalDateTime.parse(msg.timestamp, formatter)
                if (curMsgDate < earliestDate) {
                    currentUser!!.firstMessage = msg
                    earliestDate = curMsgDate
                }
            }
        }
    } catch (e: Exception) {
        println(e)
    }
    users.sortedBy { it.allMessages.length }
    try {
        println("\n Start counting occurrences")
        var totalUniqueWords = 0
        users.forEach {
            val occur = getOccurrences(it.allMessages)
            it.wordOccurrences = occur.first
            it.uniqueWordsCount = occur.second
            totalUniqueWords += it.uniqueWordsCount
        }

    } catch (e: Exception) {
        println(e)
    }
    return users.sortedBy { it.messagesTotal }.reversed()
}

private fun writeJson(list: List<Message>, outputPath: String) {
    val outerJSONObject = JSONObject()
    val messagesJsonArray = JSONArray()

    try {
        list.forEach {
            val innerJSONObject = JSONObject()
            innerJSONObject.put("senderDiscordTag", it.senderDiscordTag)
            innerJSONObject.put("senderServerTag", it.senderServerTag)
            innerJSONObject.put("content", it.content)
            innerJSONObject.put("timestamp", it.timestamp)
            innerJSONObject.put("channel", it.channel)
            messagesJsonArray.put(innerJSONObject)
        }
        outerJSONObject.put("size", list.size)
        outerJSONObject.put("messages", messagesJsonArray)

        val outputFile = File(outputPath)
        outputFile.writeText(outerJSONObject.toString(4))

    } catch (e: Exception) {
        println("At function WriteJson - $e")
    }
}

private fun writeResultJson(users: List<User>, outputPath: String, topWordsCount: Int) {
    val outerJSONObject = JSONObject()
    val usersJsonArray = JSONArray()

    try {
        users.forEach {
            val innerJSONObject = JSONObject()
            val userObject = JSONObject()
            userObject.put("DiscordTag", it.discordTag)
            userObject.put("ServerTag", it.serverTag)
            innerJSONObject.put("User", userObject)

            val firstMessageObject = JSONObject()
            firstMessageObject.put("Timestamp", it.firstMessage.timestamp)
            firstMessageObject.put("Content", it.firstMessage.content)
            firstMessageObject.put("Channel", it.firstMessage.channel)
            innerJSONObject.put("First message", firstMessageObject)

            val messagesStatObject = JSONObject()
            messagesStatObject.put("Total messages", it.messagesTotal)
            messagesStatObject.put("Total unique words (in all current user messages)", it.uniqueWordsCount)

            val topWords = JSONArray()
            val sortedList = it.wordOccurrences.toList().sortedBy { (_, value) -> value }.reversed()
            for (i in 0 until (if (sortedList.size > topWordsCount) topWordsCount else sortedList.size)) {
                val obj = JSONObject()
                obj.put(sortedList[i].first, sortedList[i].second)
                topWords.put(obj)
            }

            messagesStatObject.put("Top $topWordsCount most common words", topWords)
            innerJSONObject.put("Messages statistics", messagesStatObject)
            usersJsonArray.put(innerJSONObject)
        }
        outerJSONObject.put("size", users.size)
        outerJSONObject.put("Users", usersJsonArray)

        val outputFile = File(outputPath)
        outputFile.writeText(outerJSONObject.toString(4))

    } catch (e: Exception) {
        println("At function WriteResultJson - $e")
    }
}

private fun parseJson(path: String): List<Message> {
    fun getMessage(obj: JSONObject): Message {
        val discordTag = obj.getJSONObject("author").getString("name").trim()
        val serverTag = obj.getJSONObject("author").getString("nickname").trim()
        val msgContent = obj.getString("content").trim()
        val tStamp = obj.getString("timestamp").trim()

        val fileName = path.split("\\").last()
        val channel = fileName.substring(0 until fileName.indexOfLast { it == '.' }).trim()

        return Message(discordTag, serverTag, msgContent, tStamp, channel)
    }

    val messagesList = mutableListOf<Message>()
    try {
        val iStream = File(path).inputStream()
        val buffer = ByteArray(iStream.available())

        iStream.read(buffer)
        iStream.close()

        val json = JSONObject(String(buffer))
        val messagesJsonArr = json.getJSONArray("messages")

        for (i in 0 until messagesJsonArr.length())
            messagesList.add(getMessage(messagesJsonArr.getJSONObject(i)))
    } catch (e: Exception) {
        println("At function ParseJson - $e")
    }
    return messagesList
}

private fun parseMyJson(path: String): List<Message> {
    fun getMessage(obj: JSONObject): Message {
        val authorDiscordTag = obj.getString("senderDiscordTag")
        val authorServerTag = obj.getString("senderServerTag")
        val msgContent = obj.getString("content").trim()

        /* Initial timestamp look like: 2019-02-07T19:07:43.094+00:00*/
        val tStamp = obj.getString("timestamp").trim().split('T') as MutableList

        /* set 19:07:43.094+00:00 to 19:07:43.094 */
        tStamp[1] = tStamp[1].split('+')[0].split('.')[0]
        val channel = obj.getString("channel").trim()

        return Message(authorDiscordTag, authorServerTag, msgContent, "${tStamp[0]} ${tStamp[1]}", channel)
    }

    val messagesList = mutableListOf<Message>()
    try {
        val iStream = File(path).inputStream()
        val buffer = ByteArray(iStream.available())

        iStream.read(buffer)
        iStream.close()

        val json = JSONObject(String(buffer))
        val messagesJsonArr = json.getJSONArray("messages")

        for (i in 0 until messagesJsonArr.length())
            messagesList.add(getMessage(messagesJsonArr.getJSONObject(i)))

    } catch (e: Exception) {
        println("At function ParseMyJson - $e")
    }
    return messagesList
}

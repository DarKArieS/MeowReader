package com.nekroz.meowreader.util

/**
 * 檔名排序用的比較器。純字串比較會把「第10章」排在「第2章」前面，
 * 章節檔看起來就是亂的；這裡讓連續的數字以數值大小比較，並忽略英文字母大小寫，
 * 排出來的順序跟一般檔案管理員看到的一致。
 */
object NaturalStringComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                // 去掉前導 0 之後，位數多的數字比較大；位數相同再逐位比較。
                var startA = i
                while (startA < a.length && a[startA] == '0') startA++
                var startB = j
                while (startB < b.length && b[startB] == '0') startB++

                var endA = startA
                while (endA < a.length && a[endA].isAsciiDigit()) endA++
                var endB = startB
                while (endB < b.length && b[endB].isAsciiDigit()) endB++

                val lengthA = endA - startA
                val lengthB = endB - startB
                if (lengthA != lengthB) return lengthA - lengthB
                for (k in 0 until lengthA) {
                    val diff = a[startA + k] - b[startB + k]
                    if (diff != 0) return diff
                }
                i = endA
                j = endB
            } else {
                val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (diff != 0) return diff
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
}

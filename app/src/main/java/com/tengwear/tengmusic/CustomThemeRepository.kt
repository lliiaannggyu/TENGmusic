package com.tengwear.tengmusic

import android.content.Context
import java.io.File

/**
 * 自定义主题仓库（v1.48）。
 *
 * 主题 .json 文件保存在 App 私有目录 themes/ 下，通过应用内文件管理器导入（复制进来）。
 * 当前选中的主题用 SharedPreferences 记住文件名，重启后自动恢复。
 */
object CustomThemeRepository {
    private const val PREF = "tengmusic_themes"
    private const val KEY_PLAYER = "active_player_theme"
    private const val KEY_LYRICS = "active_lyrics_theme"

    private fun themesDir(context: Context): File =
        File(context.filesDir, "themes").also { if (!it.exists()) it.mkdirs() }

    /** 列出全部已导入主题（按文件名排序） */
    fun list(context: Context): List<ThemeFile> =
        themesDir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".json", true) }
            ?.mapNotNull { f ->
                try {
                    ThemeFile.parse(f.name, f.readText())
                } catch (_: Exception) {
                    null
                }
            }?.sortedBy { it.fileName } ?: emptyList()

    /** 导入一个主题文件（源文件可能来自任意目录）。返回导入后的 ThemeFile，失败返回 null */
    fun import(context: Context, source: File): ThemeFile? {
        val raw = try {
            source.readText()
        } catch (_: Exception) {
            return null
        }
        val parsed = ThemeFile.parse(source.name, raw) ?: return null
        if (parsed.kind == "unknown") return null
        val dest = File(themesDir(context), source.name)
        dest.writeText(raw)
        return parsed.copy(fileName = dest.name)
    }

    /** 删除主题文件 */
    fun delete(context: Context, fileName: String) {
        File(themesDir(context), fileName).let { if (it.exists()) it.delete() }
        // 若删掉的是当前选中主题，清除选择
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_PLAYER, null) == fileName) prefs.edit().remove(KEY_PLAYER).apply()
        if (prefs.getString(KEY_LYRICS, null) == fileName) prefs.edit().remove(KEY_LYRICS).apply()
    }

    fun getActiveFileName(context: Context, kind: String): String? =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(if (kind == "player") KEY_PLAYER else KEY_LYRICS, null)

    fun setActiveFileName(context: Context, kind: String, fileName: String?) {
        val key = if (kind == "player") KEY_PLAYER else KEY_LYRICS
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().let {
            if (fileName == null) it.remove(key) else it.putString(key, fileName)
        }.apply()
    }

    /** 取当前选中的主题对象 */
    fun activeTheme(context: Context, kind: String): ThemeFile? {
        val name = getActiveFileName(context, kind) ?: return null
        return list(context).firstOrNull { it.fileName == name }
    }
}

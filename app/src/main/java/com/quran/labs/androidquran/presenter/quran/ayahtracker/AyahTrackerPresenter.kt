package com.quran.labs.androidquran.presenter.quran.ayahtracker

import android.app.Activity
import android.view.MotionEvent
import com.quran.data.core.QuranInfo
import com.quran.data.model.AyahSelection
import com.quran.data.model.SuraAyah
import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.common.HighlightInfo
import com.quran.labs.androidquran.common.LocalTranslation
import com.quran.labs.androidquran.common.QuranAyahInfo
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.di.QuranPageScope
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.presenter.quran.ayahtracker.AyahTrackerPresenter.AyahInteractionHandler
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.DOUBLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.LONG_PRESS
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType.SINGLE_TAP
import com.quran.labs.androidquran.ui.helpers.AyahTracker
import com.quran.labs.androidquran.ui.helpers.HighlightType
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.view.AyahToolBar.AyahToolBarPosition
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.reading.common.ReadingEventPresenter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@QuranPageScope
class AyahTrackerPresenter @Inject constructor(
  private val quranInfo: QuranInfo,
  private val quranFileUtils: QuranFileUtils,
  private val quranDisplayData: QuranDisplayData,
  private val readingEventPresenter: ReadingEventPresenter
) : AyahTracker, Presenter<AyahInteractionHandler> {
  private val scope = MainScope()

  private var items: Array<AyahTrackerItem> = emptyArray()
  private var pendingHighlightInfo: HighlightInfo? = null

  init {
    readingEventPresenter.ayahSelectionFlow
      .onEach { onAyahSelectionChanged(it) }
      .launchIn(scope)
  }

  fun setPageBounds(pageCoordinates: PageCoordinates?) {
    for (item in items) {
      item.onSetPageBounds(pageCoordinates)
    }
  }

  fun setAyahCoordinates(ayahCoordinates: AyahCoordinates) {
    for (item in items) {
      item.onSetAyahCoordinates(ayahCoordinates)
    }

    if (pendingHighlightInfo != null && ayahCoordinates.ayahCoordinates.isNotEmpty()) {
      highlightAyah(
        pendingHighlightInfo!!.sura, pendingHighlightInfo!!.ayah,
        pendingHighlightInfo!!.highlightType, pendingHighlightInfo!!.scrollToAyah
      )
    }
  }

  fun setAyahBookmarks(bookmarks: List<Bookmark?>?) {
    for (item in items) {
      item.onSetAyahBookmarks(bookmarks!!)
    }
  }

  private fun onAyahSelectionChanged(ayahSelection: AyahSelection) {
    unHighlightAyahs(HighlightType.SELECTION)
    when (ayahSelection) {
      is AyahSelection.Ayah -> {
        val suraAyah = ayahSelection.suraAyah
        highlightAyah(suraAyah.sura, suraAyah.ayah, HighlightType.SELECTION, false)
      }
      is AyahSelection.AyahRange -> {
        items.forEach {
          val elements = quranDisplayData.getAyahKeysOnPage(
            it.page,
            ayahSelection.startSuraAyah,
            ayahSelection.endSuraAyah
          )
          it.onHighlightAyat(it.page, elements, HighlightType.SELECTION)
        }
      }
      else -> { /* nothing is selected, and we already cleared */ }
    }
  }

  override fun highlightAyah(sura: Int, ayah: Int, type: HighlightType, scrollToAyah: Boolean) {
    var handled = false
    val page = if (items.size == 1) items[0].page else quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      handled = handled || item.onHighlightAyah(page, sura, ayah, type, scrollToAyah)
    }
    pendingHighlightInfo = if (!handled) {
      HighlightInfo(sura, ayah, type, scrollToAyah)
    } else {
      null
    }
  }

  override fun highlightAyat(page: Int, ayahKeys: Set<String>, type: HighlightType) {
    for (item in items) {
      item.onHighlightAyat(page, ayahKeys, type)
    }
  }

  override fun unHighlightAyah(sura: Int, ayah: Int, type: HighlightType) {
    val page = if (items.size == 1) items[0].page else quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      item.onUnHighlightAyah(page, sura, ayah, type)
    }
  }

  override fun unHighlightAyahs(type: HighlightType) {
    for (item in items) {
      item.onUnHighlightAyahType(type)
    }
  }

  override fun getToolBarPosition(
    sura: Int, ayah: Int,
    toolBarWidth: Int, toolBarHeight: Int
  ): AyahToolBarPosition? {
    val page = if (items.size == 1) items[0].page else quranInfo.getPageFromSuraAyah(sura, ayah)
    for (item in items) {
      val position = item.getToolBarPosition(page, sura, ayah, toolBarWidth, toolBarHeight)
      if (position != null) {
        return position
      }
    }
    return null
  }

  override fun getQuranAyahInfo(sura: Int, ayah: Int): QuranAyahInfo? {
    for (item in items) {
      val quranAyahInfo = item.getQuranAyahInfo(sura, ayah)
      if (quranAyahInfo != null) {
        return quranAyahInfo
      }
    }
    return null
  }

  override fun getLocalTranslations(): Array<LocalTranslation>? {
    for (item in items) {
      val localTranslations = item.localTranslations
      if (localTranslations != null) {
        return localTranslations
      }
    }
    return null
  }

  fun onPressIgnoringSelectionState() {
    readingEventPresenter.onClick()
  }

  fun onLongPress(suraAyah: SuraAyah) {
    handleLongPress(suraAyah)
  }

  fun endAyahMode() {
    readingEventPresenter.onAyahSelection(AyahSelection.None)
  }

  fun requestMenuPositionUpdate(ayahSelectedListener: AyahSelectedListener) {
    ayahSelectedListener.requestMenuPositionUpdate(this)
  }

  fun handleTouchEvent(
    activity: Activity,
    event: MotionEvent,
    eventType: EventType,
    page: Int,
    ayahCoordinatesError: Boolean
  ): Boolean {
    if (eventType === DOUBLE_TAP) {
      readingEventPresenter.onAyahSelection(AyahSelection.None)
    } else if (eventType == LONG_PRESS ||
      readingEventPresenter.currentAyahSelection() != AyahSelection.None
    ) {
      // press or long press when an ayah is selected
      if (ayahCoordinatesError) {
        checkCoordinateData(activity)
      } else {
        // either a press or a long press, but we're in selection mode
        handleAyahSelection(event, eventType, page)
      }
    } else {
      // normal click
      readingEventPresenter.onClick()
    }
    return true
  }

  private fun handleAyahSelection(
    ev: MotionEvent,
    eventType: EventType,
    page: Int
  ) {
    val result = getAyahForPosition(page, ev.x, ev.y)
    if (result != null) {
      if (eventType == SINGLE_TAP) {
        readingEventPresenter.onAyahSelection(AyahSelection.Ayah(result))
      } else if (eventType == LONG_PRESS) {
        handleLongPress(result)
      }
    }
  }

  private fun handleLongPress(selectedSuraAyah: SuraAyah) {
    val current = readingEventPresenter.currentAyahSelection()
    val updatedAyahSelection = updateAyahRange(selectedSuraAyah, current)
    readingEventPresenter.onAyahSelection(updatedAyahSelection)
  }

  private fun updateAyahRange(selectedAyah: SuraAyah, ayahSelection: AyahSelection): AyahSelection {
    return when (ayahSelection) {
      is AyahSelection.None -> AyahSelection.Ayah(selectedAyah)
      is AyahSelection.Ayah -> {
        if (selectedAyah > ayahSelection.suraAyah) {
          AyahSelection.AyahRange(ayahSelection.suraAyah, selectedAyah)
        } else {
          AyahSelection.AyahRange(selectedAyah, ayahSelection.suraAyah)
        }
      }
      is AyahSelection.AyahRange -> {
        if (selectedAyah > ayahSelection.startSuraAyah) {
          AyahSelection.AyahRange(ayahSelection.startSuraAyah, selectedAyah)
        } else {
          AyahSelection.AyahRange(selectedAyah, ayahSelection.startSuraAyah)
        }
      }
    }
  }

  private fun getAyahForPosition(page: Int, x: Float, y: Float): SuraAyah? {
    for (item in items) {
      val ayah = item.getAyahForPosition(page, x, y)
      if (ayah != null) {
        return ayah
      }
    }
    return null
  }

  private fun checkCoordinateData(activity: Activity) {
    if (activity is PagerActivity &&
      (!quranFileUtils.haveAyaPositionFile(activity) ||
          !quranFileUtils.hasArabicSearchDatabase())
    ) {
      activity.showGetRequiredFilesDialog()
    }
  }

  override fun bind(what: AyahInteractionHandler) {
    items = what.ayahTrackerItems
  }

  override fun unbind(what: AyahInteractionHandler) {
    items = emptyArray()
    scope.cancel()
  }

  interface AyahInteractionHandler {
    val ayahTrackerItems: Array<AyahTrackerItem>
  }
}
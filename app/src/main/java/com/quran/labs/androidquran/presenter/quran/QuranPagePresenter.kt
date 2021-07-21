package com.quran.labs.androidquran.presenter.quran

import com.quran.data.model.bookmark.Bookmark
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.di.QuranPageScope
import com.quran.labs.androidquran.model.bookmark.BookmarkModel
import com.quran.labs.androidquran.model.quran.CoordinatesModel
import com.quran.labs.androidquran.presenter.Presenter
import com.quran.labs.androidquran.ui.helpers.QuranPageLoader
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableObserver
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@QuranPageScope
class QuranPagePresenter @Inject constructor(
  private val bookmarkModel: BookmarkModel,
  private val coordinatesModel: CoordinatesModel,
  private val quranSettings: QuranSettings,
  private val quranPageLoader: QuranPageLoader,
  private val pages: Array<Int>,
) : Presenter<QuranPageScreen> {

  private val compositeDisposable: CompositeDisposable = CompositeDisposable()

  private var screen: QuranPageScreen? = null
  private var encounteredError = false
  private var didDownloadImages = false

  override fun bind(screen: QuranPageScreen) {
    this.screen = screen
    if (!didDownloadImages) {
      downloadImages()
    }
    getPageCoordinates(pages)
  }

  override fun unbind(screen: QuranPageScreen) {
    this.screen = null
    compositeDisposable.clear()
  }

  private fun getPageCoordinates(pages: Array<out Int>) {
    compositeDisposable.add(
      coordinatesModel.getPageCoordinates(quranSettings.shouldOverlayPageInfo(), *pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableObserver<PageCoordinates?>() {
          override fun onNext(pageCoordinates: PageCoordinates) {
            screen?.setPageCoordinates(pageCoordinates)
          }

          override fun onError(e: Throwable) {
            encounteredError = true
            screen?.setAyahCoordinatesError()
          }

          override fun onComplete() {
            getAyahCoordinates(pages)
          }
        })
    )
  }

  private fun getBookmarkedAyahs(pages: Array<out Int>) {
    compositeDisposable.add(
      bookmarkModel.getBookmarkedAyahsOnPageObservable(*pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableObserver<List<Bookmark?>?>() {
          override fun onNext(bookmarks: List<Bookmark?>) {
            screen?.setBookmarksOnPage(bookmarks)
          }

          override fun onError(e: Throwable) {}

          override fun onComplete() {}
        })
    )
  }

  private fun getAyahCoordinates(pages: Array<out Int>) {
    compositeDisposable.add(
      Completable.timer(500, TimeUnit.MILLISECONDS)
        .andThen(Observable.fromArray(*pages))
        .flatMap { coordinatesModel.getAyahCoordinates(it) }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableObserver<AyahCoordinates?>() {
          override fun onNext(coordinates: AyahCoordinates) {
            screen?.setAyahCoordinatesData(coordinates)
          }

          override fun onError(e: Throwable) {}

          override fun onComplete() {
            if (quranSettings.shouldHighlightBookmarks()) {
              getBookmarkedAyahs(pages)
            }
          }
        })
    )
  }

  fun downloadImages() {
    screen?.hidePageDownloadError()
    compositeDisposable.add(
      quranPageLoader.loadPages(*pages)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribeWith(object : DisposableObserver<Response?>() {
          override fun onNext(response: Response) {
            screen?.let { pageScreen ->
              val bitmap = response.bitmap
              if (bitmap != null) {
                didDownloadImages = true
                pageScreen.setPageBitmap(response.pageNumber, bitmap)
              } else {
                didDownloadImages = false
                val errorRes: Int = when (response.errorCode) {
                    Response.ERROR_SD_CARD_NOT_FOUND -> R.string.sdcard_error
                    Response.ERROR_NO_INTERNET, Response.ERROR_DOWNLOADING_ERROR -> R.string.download_error_network
                    else -> R.string.download_error_general
                  }
                pageScreen.setPageDownloadError(errorRes)
              }
            }
          }

          override fun onError(e: Throwable) {}

          override fun onComplete() {}
        })
    )
  }

  fun refresh() {
    if (encounteredError) {
      encounteredError = false
      getPageCoordinates(pages)
    }
  }
}

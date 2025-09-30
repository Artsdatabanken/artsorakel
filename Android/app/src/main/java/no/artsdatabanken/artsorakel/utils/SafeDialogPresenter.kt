package no.artsdatabanken.artsorakel.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment

object SafeDialogPresenter {

    fun showSafely(
        activity: AppCompatActivity,
        dialogProvider: () -> DialogFragment,
        tag: String
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val fm = activity.supportFragmentManager
        if (fm.isStateSaved) return

        val existing = fm.findFragmentByTag(tag)
        if (existing is DialogFragment && existing.isAdded) return

        dialogProvider().show(fm, tag)
    }
}


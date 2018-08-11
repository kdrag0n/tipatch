package com.kdrag0n.tipatch

import android.content.Context
import android.net.Uri
import com.danielstone.materialaboutlibrary.ConvenienceBuilder as ItemBuilder
import com.danielstone.materialaboutlibrary.MaterialAboutActivity
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList

class AboutActivity : MaterialAboutActivity() {
    override fun getMaterialAboutList(ctx: Context): MaterialAboutList {
        val appCard = MaterialAboutCard.Builder()
                .addItem(MaterialAboutTitleItem.Builder()
                        .text(R.string.app_name)
                        .icon(R.mipmap.ic_launcher)
                        .build())
                .addItem(ItemBuilder.createVersionActionItem(ctx,
                        getDrawable(R.drawable.ic_info_outline),
                        "Version",
                        false))
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_github)
                        .text(R.string.about_src)
                        .subText(R.string.about_src_desc)
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse(R.string.source_uri())
                        ))
                        .build())
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_money)
                        .text(R.string.donate)
                        .subText(R.string.about_donate_desc)
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse(R.string.donate_uri())))
                        .build())
                .build()

        val authorCard = MaterialAboutCard.Builder()
                .title(R.string.author)
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_person)
                        .text(R.string.author_name)
                        .subText(R.string.author_nick)
                        .build())
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_send)
                        .text("Telegram")
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse(R.string.telegram_uri())))
                        .build())
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_github)
                        .text("GitHub")
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse(R.string.github_uri())))
                        .build())
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_xda)
                        .text("XDA-Developers")
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse(R.string.xda_uri())))
                        .build())
                .addItem(MaterialAboutActionItem.Builder()
                        .icon(R.drawable.ic_email)
                        .text("Email")
                        .setOnClickAction(ItemBuilder.createWebsiteOnClickAction(ctx, Uri.parse("mailto:" + R.string.contact_mail().replace(" (at) ", "@"))))
                        .build())
                .build()

        return MaterialAboutList.Builder()
                .addCard(appCard)
                .addCard(authorCard)
                .build()
    }

    override fun getActivityTitle(): CharSequence? {
        return R.string.about()
    }

    private operator fun Int.invoke(vararg fmt: Any): String {
        return resources.getString(this, *fmt)
    }
}

# Swahili review — `values-sw/strings.xml`

**Status: reviewed and approved — Polycarp Okock, 13 September 2026. Cleared to ship.**

These strings were produced with machine assistance and then reviewed by a
first-language speaker. Tone and register are what machine translation usually
gets wrong, and this is the app's home audience, so the review was the gate on
shipping the locale rather than a formality.

Keep this file with the strings. When `values-sw/strings.xml` gains an entry, it
is unreviewed until it appears here with a sign-off — the table below is the
record of what was actually checked.

## How to review

Fill in the **Correction** column for anything that reads oddly. Leave it blank
if the proposal is fine. You do not need to touch any XML — hand this file (or
`translation-review-sw.csv`, which opens in a spreadsheet) back and the
corrections get applied.

Two rules for the reviewer:

1. **Keep the `%1$s` / `%1$d` markers exactly as they appear**, including the
   order. They are replaced at runtime with a station name, a file name or a
   number.
2. Technical terms — Cast, Wi-Fi, MP3, AAC, HLS, Android — were deliberately
   left in English, which is how they are used in Kenya. Change them if you
   disagree.

## Sign-off

- **Reviewed by: Polycarp Okock — 13 September 2026**
- [x] Tone and register are right for a Kenyan audience
- [x] Placeholders are intact
- [x] Safe to ship

Strings added after this date are **not** covered by this review:

| Added after sign-off | Reviewed |
|---|---|
| `recordings_share_confirm_title` | [ ] |
| `recordings_share_confirm_message` | [ ] |
| `recordings_share_confirm_action` | [ ] |
| `player_play` — Cheza | [ ] |
| `player_pause` — Sitisha | [ ] |
| `player_live` — MOJA KWA MOJA | [ ] |
| `player_expand` — Fungua kichezaji cha skrini nzima | [ ] |
| `player_collapse` — Funga kichezaji cha skrini nzima | [ ] |
| `settings_language` — Lugha | [ ] |
| `settings_language_system` — Fuata mfumo | [ ] |
| `settings_language_en` — Kiingereza | [ ] |
| `settings_language_sw` — Kiswahili | [ ] |
| `onboarding_message_welcome` — extended: "Inapatikana kwa Kiingereza na Kiswahili." | [ ] |
| `onboarding_message_listen` — extended: "Gusa kichezaji kidogo chini ili kukifungua kwa skrini nzima." | [ ] |

---

## Priority — 16 entries

Long copy, legal wording, and anything carrying a technical term.

| Key | English | Swahili (proposed) | Correction |
|---|---|---|---|
| `error_wifi_only` | Wi-Fi only is on, and you\'re not on Wi-Fi. Change it in Settings. | Umewasha Wi-Fi pekee, na huko kwenye Wi-Fi. Badilisha katika Mipangilio. | |
| `recording_unavailable` | This station only offers an HLS stream, which can\'t be recorded directly. | Kituo hiki kina mkondo wa HLS pekee, ambao hauwezi kurekodiwa moja kwa moja. | |
| `recording_needs_storage_permission` | Storage access is needed to save recordings on this version of Android. | Ruhusa ya hifadhi inahitajika ili kuhifadhi rekodi kwenye toleo hili la Android. | |
| `recording_timed_out` | %1$s\n\nAndroid limits background recording to six hours a day, so it stopped there. | %1$s\n\nAndroid huruhusu kurekodi chinichini kwa saa sita kwa siku, kwa hivyo kumesimama hapo. | |
| `onboarding_message_welcome` | Live Kenyan radio wherever you are. Dozens of stations, free, and no account to create. | Redio za Kenya moja kwa moja, popote ulipo. Vituo vingi, bila malipo, na huhitaji akaunti. | |
| `onboarding_message_find` | Search by name, and tap the heart on any station to keep it under Favourites for one-tap access. | Tafuta kwa jina, kisha gusa moyo kwenye kituo chochote ili kikae chini ya Vipendwa na ukifikie kwa mguso mmoja. | |
| `onboarding_message_listen` | Playback carries on in the background and from the lock screen, and you can cast any station to a Google Cast speaker or TV on your network. | Usikilizaji unaendelea chinichini na kwenye skrini iliyofungwa, na unaweza kutuma kituo chochote kwenye spika au TV yenye Google Cast kwenye mtandao wako. | |
| `onboarding_message_record` | Tap record while a station is playing. Recordings are saved to Music/Radio Diaspora and open in any music app — as MP3 where the station broadcasts in MP3, with nothing re-encoded. | Gusa kitufe cha kurekodi wakati kituo kinasikika. Rekodi huhifadhiwa katika Music/Radio Diaspora na hufunguka katika programu yoyote ya muziki — kama MP3 pale kituo kinaporusha kwa MP3, bila kubadilisha muundo. | |
| `onboarding_message_agreement` | This app streams content that is publicly available from Kenyan radio broadcasters. We do not host or own that content; all rights belong to the respective owners. Anything you record is for your personal use only. This app is provided for personal, non-commercial use. | Programu hii hurusha maudhui yanayopatikana hadharani kutoka kwa watangazaji wa redio wa Kenya. Hatuhifadhi wala kumiliki maudhui hayo; haki zote ni za wamiliki husika. Chochote unachorekodi ni kwa matumizi yako binafsi pekee. Programu hii ni kwa matumizi binafsi, si ya kibiashara. | |
| `settings_wifi_only_title` | Wi-Fi only | Wi-Fi pekee | |
| `settings_recording_limit_none` | No limit (Android stops at 6 hours) | Bila kikomo (Android husimamisha baada ya saa 6) | |
| `settings_recording_format_summary` | Recorded exactly as the station broadcasts it — MP3 where offered, otherwise AAC. No re-encoding, so nothing is lost. | Hurekodiwa kama kituo kinavyorusha — MP3 pale inapopatikana, vinginevyo AAC. Hakuna kubadilisha muundo, hivyo hakuna ubora unaopotea. | |
| `settings_privacy_heading` | Privacy | Faragha | |
| `settings_privacy_body` | This app does not collect, store or share any personal data. The permissions it requests are used only for playback and recording: internet access to stream audio, a foreground service so playback and recording continue in the background, and nearby-devices access so Cast targets can be found. | Programu hii haikusanyi, haihifadhi wala haishiriki data yoyote binafsi. Ruhusa inazoomba hutumika kwa usikilizaji na kurekodi pekee: intaneti kwa ajili ya sauti, huduma ya mbele ili usikilizaji na kurekodi viendelee chinichini, na ruhusa ya vifaa vilivyo karibu ili vifaa vya Cast vipatikane. | |
| `settings_licence_heading` | Licence | Leseni | |
| `settings_licence_body` | Released under the Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International licence. You may share and adapt the material with attribution, for non-commercial use, under the same licence. | Imetolewa chini ya leseni ya Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International. Unaweza kushiriki na kubadilisha maudhui ukitoa sifa, kwa matumizi yasiyo ya kibiashara, chini ya leseni hiyo hiyo. | |

---

## Everything else — 89 entries

Short labels and buttons. Quick to skim.

| Key | English | Swahili (proposed) | Correction |
|---|---|---|---|
| `action_settings` | Settings | Mipangilio | |
| `action_about` | About | Kuhusu | |
| `action_refresh` | Refresh stations | Onyesha vituo upya | |
| `action_recordings` | Recordings | Rekodi | |
| `search_hint` | Search stations | Tafuta vituo | |
| `search_clear` | Clear search | Futa utafutaji | |
| `filter_all` | All | Zote | |
| `filter_favourites` | Favourites | Vipendwa | |
| `station_logo_of` | Logo for %1$s | Nembo ya %1$s | |
| `play_station` | Play %1$s | Sikiliza %1$s | |
| `now_playing_badge` | Live | Hewani | |
| `add_to_favourites` | Add %1$s to favourites | Ongeza %1$s kwenye vipendwa | |
| `remove_from_favourites` | Remove %1$s from favourites | Ondoa %1$s kwenye vipendwa | |
| `empty_title` | No stations found | Hakuna kituo kilichopatikana | |
| `empty_message` | Try a different search term. | Jaribu neno lingine la utafutaji. | |
| `empty_favourites_title` | No favourites yet | Bado huna vipendwa | |
| `empty_favourites_message` | Tap the heart on any station to keep it here. | Gusa moyo kwenye kituo chochote ili kikae hapa. | |
| `error_title` | Couldn\'t load stations | Imeshindwa kupakia vituo | |
| `error_message` | Check your connection and try again. | Angalia muunganisho wako kisha ujaribu tena. | |
| `error_retry` | Retry | Jaribu tena | |
| `offline_title` | You\'re offline | Huna muunganisho | |
| `offline_message` | No internet connection. Reconnect and the list will load automatically. | Hakuna intaneti. Unganisha tena na orodha itapakia yenyewe. | |
| `error_stream_unavailable` | That station isn\'t streaming right now. | Kituo hicho hakirushi matangazo kwa sasa. | |
| `error_notification_permission` | Notifications are needed to keep playing in the background. | Arifa zinahitajika ili usikilizaji uendelee chinichini. | |
| `player_connecting` | Connecting… | Inaunganisha… | |
| `player_stop` | Stop playback | Simamisha usikilizaji | |
| `player_artwork` | Artwork for the station now playing | Picha ya kituo kinachosikika sasa | |
| `player_casting_to` | Playing on %1$s | Inasikika kwenye %1$s | |
| `recording_start` | Record this station | Rekodi kituo hiki | |
| `recording_stop` | Stop recording | Simamisha kurekodi | |
| `recording_started` | Recording %1$s | Inarekodi %1$s | |
| `recording_saved` | Saved as %1$s | Imehifadhiwa kama %1$s | |
| `recording_failed_storage` | Couldn\'t create the file. Check available storage. | Imeshindwa kutengeneza faili. Angalia nafasi ya hifadhi. | |
| `recording_failed_empty` | Nothing was recorded — the stream didn\'t send any audio. | Hakuna kilichorekodiwa — kituo hakikutuma sauti yoyote. | |
| `recording_notification_title` | Recording %1$s | Inarekodi %1$s | |
| `recording_notification_done_title` | Recording finished | Kurekodi kumekamilika | |
| `recording_channel_name` | Recording | Kurekodi | |
| `recording_channel_description` | Shows progress while a station is being recorded | Huonyesha maendeleo wakati kituo kinarekodiwa | |
| `recording_in_progress` | Recording · %1$s | Inarekodi · %1$s | |
| `recordings_title` | Recordings | Rekodi | |
| `recordings_empty_title` | No recordings yet | Bado hakuna rekodi | |
| `recordings_empty_message` | Tap the record button while a station is playing. | Gusa kitufe cha kurekodi wakati kituo kinasikika. | |
| `recordings_location` | Saved in Music/Radio Diaspora | Zimehifadhiwa katika Music/Radio Diaspora | |
| `recordings_play` | Play | Sikiliza | |
| `recordings_share` | Share | Shiriki | |
| `recordings_delete` | Delete | Futa | |
| `recordings_delete_confirm_title` | Delete recording? | Ufute rekodi hii? | |
| `recordings_delete_confirm_message` | %1$s will be removed from this device. | %1$s itaondolewa kwenye kifaa hiki. | |
| `recordings_deleted` | Recording deleted | Rekodi imefutwa | |
| `recordings_delete_failed` | Couldn\'t delete that recording | Imeshindwa kufuta rekodi hiyo | |
| `recordings_no_player` | No app on this device can play that file | Hakuna programu kwenye kifaa hiki inayoweza kusikiliza faili hiyo | |
| `recordings_share_via` | Share recording | Shiriki rekodi | |
| `onboarding_previous` | Back | Rudi | |
| `onboarding_next` | Next | Endelea | |
| `onboarding_skip` | Skip | Ruka | |
| `onboarding_finish` | Agree &amp; continue | Kubali na uendelee | |
| `onboarding_title_welcome` | Welcome to Radio Diaspora | Karibu Radio Diaspora | |
| `onboarding_title_find` | Find your stations | Tafuta vituo vyako | |
| `onboarding_title_listen` | Listen anywhere | Sikiliza popote | |
| `onboarding_title_record` | Record what you hear | Rekodi unachosikia | |
| `onboarding_title_agreement` | User agreement | Makubaliano ya mtumiaji | |
| `settings_title` | Settings | Mipangilio | |
| `settings_section_appearance` | Appearance | Muonekano | |
| `settings_theme` | Theme | Mandhari | |
| `settings_theme_system` | Follow system | Fuata mfumo | |
| `settings_theme_light` | Light | Nuru | |
| `settings_theme_dark` | Dark | Giza | |
| `settings_section_playback` | Playback | Usikilizaji | |
| `settings_resume_title` | Resume last station | Endeleza kituo cha mwisho | |
| `settings_resume_summary` | Start playing the station you last listened to when the app opens | Anza kusikiliza kituo ulichosikiliza mwisho programu inapofunguliwa | |
| `settings_keep_screen_on_title` | Keep the screen on | Weka skrini iwe wazi | |
| `settings_keep_screen_on_summary` | Stop the display sleeping while a station is playing | Zuia skrini kuzima wakati kituo kinasikika | |
| `settings_section_data` | Data | Data | |
| `settings_wifi_only_summary` | Refuse to stream or record over mobile data | Usisikilize wala kurekodi kwa data ya simu | |
| `settings_section_recording` | Recording | Kurekodi | |
| `settings_recording_limit` | Maximum length | Urefu wa juu | |
| `settings_recording_limit_minutes` | %1$d minutes | Dakika %1$d | |
| `settings_recording_format_title` | Format | Muundo | |
| `settings_section_storage` | Storage | Hifadhi | |
| `settings_clear_cache_title` | Clear image cache | Futa akiba ya picha | |
| `settings_clear_cache_summary` | Remove downloaded station logos | Ondoa nembo za vituo zilizopakuliwa | |
| `settings_cache_cleared` | Image cache cleared | Akiba ya picha imefutwa | |
| `settings_section_about` | About | Kuhusu | |
| `settings_replay_onboarding_title` | Show the welcome screens again | Onyesha skrini za karibu tena | |
| `settings_replay_onboarding_summary` | The introduction will appear the next time the app starts | Utangulizi utaonekana wakati ujao programu itakapoanza | |
| `settings_onboarding_reset` | The welcome screens will show next time | Skrini za karibu zitaonekana wakati ujao | |
| `about_title` | About | Kuhusu | |
| `about_version` | Version %1$s | Toleo %1$s | |
| `about_created_by` | Created by | Imetengenezwa na | |

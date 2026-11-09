#pragma once

/**
 * Picking a Wi-Fi on the device: the networks Gaby can see (signal, lock,
 * saved), a password screen with a keyboard, and the saved list where a
 * network is forgotten. Taps become net::Jobs; ui.cpp decides when the
 * picker opens and where it goes back to.
 */
namespace wifi_screens {

/** `onBack` runs when the user leaves without joining anything. */
void build(void (*onBack)());

/** lost = no known network answered: no way back, and Gaby says so. */
void open(bool lost);

// The network task's answers, as they arrive.
void listChanged();
void joining(const char *ssid);
void joined();
void failed(const char *why);

}  // namespace wifi_screens

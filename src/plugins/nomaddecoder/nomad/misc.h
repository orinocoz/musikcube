/*
 * Copyright 2008-2013 Various Authors
 * Copyright 2004 Timo Hirvonen
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses/>.
 */

#ifndef CMUS_MISC_H
#define CMUS_MISC_H

#include <stddef.h>

#define d_print(format, ...) \
    do { \
        fprintf(stderr, format, ##__VA_ARGS__); \
    } while(0)

/*
 * @field   contains Replay Gain data format in bit representation
 * @gain    pointer where to store gain value * 10
 *
 * Returns 0 if @field doesn't contain a valid gain value,
 *         1 for track (= radio) adjustment
 *         2 for album (= audiophile) adjustment
 *
 * http://replaygain.hydrogenaudio.org/rg_data_format.html
 */
int replaygain_decode(unsigned int field, int *gain);

#endif

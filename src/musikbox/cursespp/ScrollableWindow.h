//////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2007-2016 musikcube team
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
//    * Redistributions of source code must retain the above copyright notice,
//      this list of conditions and the following disclaimer.
//
//    * Redistributions in binary form must reproduce the above copyright
//      notice, this list of conditions and the following disclaimer in the
//      documentation and/or other materials provided with the distribution.
//
//    * Neither the name of the author nor the names of other contributors may
//      be used to endorse or promote products derived from this software
//      without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.
//
//////////////////////////////////////////////////////////////////////////////

#pragma once

#include "curses_config.h"
#include "Window.h"
#include "IScrollAdapter.h"
#include "IScrollable.h"
#include "IKeyHandler.h"

namespace cursespp {
    class ScrollableWindow : public IScrollable, public IKeyHandler, public Window {
        public:
            ScrollableWindow(IWindow *parent = NULL);
            virtual ~ScrollableWindow();

            virtual void Show();
            virtual void OnDimensionsChanged();

            virtual bool KeyPress(const std::string& key);

            virtual void ScrollToTop();
            virtual void ScrollToBottom();
            virtual void ScrollUp(int delta = 1);
            virtual void ScrollDown(int delta = 1);
            virtual void PageUp();
            virtual void PageDown();

            virtual void Focus();
            virtual void Blur();

            virtual void OnAdapterChanged();

            void SetAllowArrowKeyPropagation(bool allow = true);

            const IScrollAdapter::ScrollPosition& GetScrollPosition() const;

        protected:
            virtual IScrollAdapter& GetScrollAdapter() = 0;
            virtual IScrollAdapter::ScrollPosition& GetMutableScrollPosition();

            size_t GetPreviousPageEntryIndex();
            bool IsLastItemVisible();

        private:
            IScrollAdapter::ScrollPosition scrollPosition;
            bool allowArrowKeyPropagation;
    };
}

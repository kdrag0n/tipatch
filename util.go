package tipatch

import (
	"errors"

	"github.com/hashicorp/errwrap"
)

// GetErrors returns the wrapped errors from one error.
func GetErrors(err error) []string {
	if err != nil {
		wrapped := err.(errwrap.Wrapper).WrappedErrors()
		return []string{wrapped[0].Error(), wrapped[1].Error()}
	}

	return []string{}
}

func eMsg(err error, msg string) error {
	return errwrap.Wrap(errors.New(msg+";"+err.Error()), err)
}

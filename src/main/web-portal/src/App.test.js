import { render, screen, waitFor } from '@testing-library/react';
import App from './App';
import {act} from 'react-dom/test-utils';
import {Simulate} from "react-dom/test-utils";

// We use ReactTestUtils to simulate user input!

function wait( milliseconds ) {
  return new Promise(resolve => {
    setTimeout(resolve, milliseconds);
  });
  // Because we use web sockets, we need to wait for answers (its not regular POST/GET)
}

test('Test login form', async () => {
  const dom = render(<App/>);
  // The page is loaded using websockets, so we need to wait for the page to load
  // We expect the page to load in 1 second:
  await act(() => wait(1000));
  // First we expect there to be some text saying 'Tribee Login!'
  const titleElement = await waitFor(() => screen.getByText(/Tribee Login!/i));
  expect(titleElement).toBeInTheDocument();

  // First of all, we expect the login page to be loaded, meaning a username and password input
  const username = await waitFor(() => screen.getByPlaceholderText(/Username/i));
  const password = await waitFor(() => screen.getByPlaceholderText(/Password/i));
  // The login page has 2 simple inputs and a feedback text in a 'span' element with id 'login-feedback'
  // before entering in any text, the feedback text should be empty
  const feedbackText = dom.container.querySelector('#login-feedback');
  expect(feedbackText).toHaveTextContent('');
  // Now we simulate the user typing in the username and password
  // First username:
  // Let's type in the username so that it also triggers the onChange event
  await act(async () => {
    await Simulate.change(username, {target: {value: 'a'}});
  });

  // We are doing MVVM, so let's wait for the view-model to update the feedback text
  await act(() => wait(1000));

  // We print the feedback text to the console
  console.log(feedbackText.textContent);

  // Now we expect the feedback text to be 'Username must be at least 3 characters long'
  expect(feedbackText.textContent).toContain('Username');
  expect(feedbackText.textContent).toContain('3');
});

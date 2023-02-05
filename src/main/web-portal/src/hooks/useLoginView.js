import { useState } from 'react';

export default function useLoginView(vm) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [feedback, setFeedback] = useState('');
  const [feedbackColor, setFeedbackColor] = useState('black');

  console.log('LoginView: ' + vm);

  return {
    username,
    password,
    feedback,
    feedbackColor,
    setUsername,
    setPassword,
    setFeedback,
    setFeedbackColor,
  };
}
